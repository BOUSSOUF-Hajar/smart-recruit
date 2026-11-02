package com.smartrecruit.identity.application;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.identity.domain.RefreshToken;
import com.smartrecruit.identity.domain.RevocationReason;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.RefreshTokenRepository;
import com.smartrecruit.identity.infrastructure.UserRepository;
import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** Compte de l'utilisateur connecté : lecture ({@code /me}) et changement de mot de passe (US-11). */
@Service
public class AccountService {

    static final String PASSWORD_CHANGED = "PASSWORD_CHANGED";

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final SessionService sessions;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder audit;
    private final Clock clock;

    public AccountService(UserRepository users, RefreshTokenRepository refreshTokens, SessionService sessions,
                          PasswordEncoder passwordEncoder, AuditRecorder audit, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.sessions = sessions;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public AccountView me(CurrentUser current) {
        User user = load(current.id());
        return new AccountView(user, sessions.companyOf(user));
    }

    /**
     * Vérifie l'ancien mot de passe, enregistre le nouveau et ferme les autres sessions :
     * si le mot de passe a fuité, l'intrus perd son accès (UC-07).
     * <p>
     * La session courante est reconnue grâce à son cookie (envoyé, car son chemin couvre
     * {@code /api/v1/auth/password}) et conservée : le frontend appelle ensuite {@code /auth/refresh}
     * pour obtenir un access token sans le marqueur {@code pwdChange}.
     */
    @Transactional
    public void changePassword(CurrentUser current, String currentPassword, String newPassword,
                               String presentedRefreshToken) {
        User user = load(current.id());
        if (!passwordEncoder.matches(currentPassword, user.getPasswordHash())) {
            throw new BusinessRuleViolationException(ErrorCode.INVALID_CURRENT_PASSWORD,
                    "Le mot de passe actuel est incorrect.");
        }
        user.changePassword(Objects.requireNonNull(passwordEncoder.encode(newPassword)));

        Instant now = clock.instant();
        Optional<UUID> currentSession = currentSession(user, presentedRefreshToken, now);
        int revoked = currentSession
                .map(keptId -> refreshTokens.revokeAllForUserExcept(user.getId(), keptId, now,
                        RevocationReason.PASSWORD_CHANGED))
                .orElseGet(() -> refreshTokens.revokeAllForUser(user.getId(), now, RevocationReason.PASSWORD_CHANGED));
        audit.record(AuditEvent.success(PASSWORD_CHANGED).by(user.getId(), user.getEmail())
                .on("USER", user.getId()).with("revokedSessions", revoked));
    }

    private Optional<UUID> currentSession(User user, String presentedRefreshToken, Instant now) {
        if (presentedRefreshToken == null || presentedRefreshToken.isBlank()) {
            return Optional.empty();
        }
        return refreshTokens.findByTokenHash(SessionTokens.hash(presentedRefreshToken))
                .filter(token -> token.getUserId().equals(user.getId()))
                .filter(token -> !token.isRevoked() && !token.isExpired(now))
                .map(RefreshToken::getId);
    }

    private User load(UUID id) {
        // Compte supprimé pendant la validité du token : rare, traité comme une ressource absente.
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Compte"));
    }

    /** Compte et entreprise, pour construire la réponse de {@code /me}. */
    public record AccountView(User user, UUID companyId) {
    }
}
