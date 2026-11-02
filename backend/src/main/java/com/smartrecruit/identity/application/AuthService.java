package com.smartrecruit.identity.application;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.identity.api.CandidateRegistered;
import com.smartrecruit.identity.domain.RefreshToken;
import com.smartrecruit.identity.domain.RevocationReason;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.RefreshTokenRepository;
import com.smartrecruit.identity.infrastructure.UserRepository;
import com.smartrecruit.shared.domain.AuthenticationFailedException;
import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.RateLimitExceededException;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;

/**
 * Inscription, connexion, rotation de session et déconnexion (US-06 à US-09).
 */
@Service
public class AuthService {

    static final String LOGIN = "LOGIN";
    static final String LOGOUT = "LOGOUT";
    static final String TOKEN_REUSE_DETECTED = "TOKEN_REUSE_DETECTED";
    static final String USER_REGISTERED = "USER_REGISTERED";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final AuthRateLimits rateLimits;
    private final AuditRecorder audit;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * Hash d'un mot de passe quelconque, comparé quand l'email est inconnu : la réponse prend alors
     * le même temps qu'un mauvais mot de passe, et ne révèle pas quels emails ont un compte.
     */
    private final String dummyHash;

    public AuthService(UserRepository users, RefreshTokenRepository refreshTokens, SessionService sessions,
                       PasswordEncoder passwordEncoder, AuthRateLimits rateLimits, AuditRecorder audit,
                       ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.rateLimits = rateLimits;
        this.audit = audit;
        this.events = events;
        this.clock = clock;
        this.dummyHash = passwordEncoder.encode("smartrecruit-timing-equalizer");
    }

    /**
     * US-06. Rôle toujours CANDIDATE (RG-02) ; le profil vide est créé par le module Candidats,
     * dans la même transaction, en réaction à {@link CandidateRegistered}.
     */
    @Transactional
    public AuthResult register(RegistrationCommand command, ClientInfo client) {
        rateLimits.checkAndRecordRegistration(client.ip());
        String email = User.normalizeEmail(command.email());
        if (users.existsByEmail(email)) {
            throw emailAlreadyUsed();
        }
        User user = User.registerCandidate(email, Objects.requireNonNull(passwordEncoder.encode(command.password())),
                command.firstName(), command.lastName(), clock.instant());
        // saveAndFlush : une inscription concurrente avec le même email échoue ici, sur l'index
        // unique uk_app_user_email, traduit en EMAIL_ALREADY_USED par le GlobalExceptionHandler.
        users.saveAndFlush(user);
        events.publishEvent(new CandidateRegistered(user.getId()));
        audit.record(AuditEvent.success(USER_REGISTERED).by(user.getId(), user.getEmail()).on("USER", user.getId()));
        return sessions.open(user, client);
    }

    /**
     * US-07. Même réponse pour un email inconnu, un mauvais mot de passe et un compte désactivé
     * (F-AUTH-02) ; seul le journal d'audit distingue les trois cas.
     */
    @Transactional
    public AuthResult login(String rawEmail, String password, ClientInfo client) {
        String email = User.normalizeEmail(rawEmail);
        try {
            rateLimits.checkLogin(email);
        } catch (RateLimitExceededException e) {
            audit.recordIndependently(AuditEvent.failure(LOGIN).by(null, email).with("reason", "RATE_LIMITED"));
            throw e;
        }

        Optional<User> found = users.findByEmail(email);
        if (found.isEmpty()) {
            passwordEncoder.matches(password, dummyHash);
            throw loginFailed(email, null, "UNKNOWN_EMAIL");
        }
        User user = found.get();
        if (!passwordEncoder.matches(password, user.getPasswordHash())) {
            throw loginFailed(email, user, "BAD_PASSWORD");
        }
        if (!user.isEnabled()) {
            throw loginFailed(email, user, "DISABLED");
        }

        rateLimits.loginSucceeded(email);
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.rehashPassword(Objects.requireNonNull(passwordEncoder.encode(password)));
        }
        user.recordLogin(clock.instant());
        audit.record(AuditEvent.success(LOGIN).by(user.getId(), user.getEmail()));
        return sessions.open(user, client);
    }

    /**
     * US-08. Rotation : l'ancien jeton est révoqué, un nouveau est émis. Présenter un jeton déjà
     * remplacé par rotation signifie qu'il a été copié : toutes les sessions du compte sont fermées.
     * Un jeton fermé par une déconnexion ou un changement de mot de passe est seulement refusé.
     * <p>
     * {@code noRollbackFor} : la révocation de toutes les sessions doit être enregistrée
     * même si la requête se termine en erreur.
     */
    @Transactional(noRollbackFor = AuthenticationFailedException.class)
    public AuthResult refresh(String presentedToken, ClientInfo client) {
        if (presentedToken == null || presentedToken.isBlank()) {
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        Instant now = clock.instant();
        RefreshToken token = refreshTokens.findForUpdate(SessionTokens.hash(presentedToken))
                .orElseThrow(AuthenticationFailedException::invalidRefreshToken);
        if (token.wasRotated()) {
            int revoked = refreshTokens.revokeAllForUser(token.getUserId(), now, RevocationReason.REUSE_DETECTED);
            audit.record(AuditEvent.failure(TOKEN_REUSE_DETECTED).by(token.getUserId(), null)
                    .on("SESSION", token.getId()).with("revokedSessions", revoked));
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        if (token.isRevoked()) {
            // Fermée par une déconnexion ou un changement de mot de passe : refus simple
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        if (token.isExpired(now)) {
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        User user = users.findById(token.getUserId()).orElseThrow(AuthenticationFailedException::invalidRefreshToken);
        if (!user.isEnabled()) {
            token.revoke(now, RevocationReason.ACCOUNT_DISABLED);
            throw AuthenticationFailedException.invalidRefreshToken();
        }
        token.revoke(now, RevocationReason.ROTATED);
        return sessions.open(user, client);
    }

    /** US-09. Idempotent : un jeton absent, inconnu ou déjà révoqué ne provoque pas d'erreur. */
    @Transactional
    public void logout(String presentedToken) {
        if (presentedToken == null || presentedToken.isBlank()) {
            return;
        }
        refreshTokens.findForUpdate(SessionTokens.hash(presentedToken))
                .filter(token -> !token.isRevoked())
                .ifPresent(token -> {
                    token.revoke(clock.instant(), RevocationReason.LOGOUT);
                    audit.record(AuditEvent.success(LOGOUT).by(token.getUserId(), null).on("SESSION", token.getId()));
                });
    }

    private AuthenticationFailedException loginFailed(String email, User user, String reason) {
        rateLimits.loginFailed(email);
        audit.recordIndependently(AuditEvent.failure(LOGIN)
                .by(user == null ? null : user.getId(), email)
                .with("reason", reason));
        return AuthenticationFailedException.invalidCredentials();
    }

    static BusinessRuleViolationException emailAlreadyUsed() {
        return new BusinessRuleViolationException(ErrorCode.EMAIL_ALREADY_USED, "Un compte existe déjà avec cet email.");
    }
}
