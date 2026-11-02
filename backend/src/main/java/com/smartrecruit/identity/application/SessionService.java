package com.smartrecruit.identity.application;

import com.smartrecruit.identity.api.RecruiterCompanyLookup;
import com.smartrecruit.identity.domain.RefreshToken;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.RefreshTokenRepository;
import com.smartrecruit.shared.security.JwtService;
import com.smartrecruit.shared.security.Role;
import com.smartrecruit.shared.security.SecurityProperties;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * Ouverture d'une session : un access token (JWT) et un refresh token stocké sous forme d'empreinte.
 * Appelé uniquement depuis les services du module, dans leur transaction.
 */
@Service
class SessionService {

    private final RefreshTokenRepository refreshTokens;
    private final JwtService jwtService;
    private final ObjectProvider<RecruiterCompanyLookup> companyLookup;
    private final SecurityProperties.Jwt jwt;
    private final Clock clock;

    SessionService(RefreshTokenRepository refreshTokens, JwtService jwtService,
                   ObjectProvider<RecruiterCompanyLookup> companyLookup, SecurityProperties properties, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.jwtService = jwtService;
        this.companyLookup = companyLookup;
        this.jwt = properties.jwt();
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public AuthResult open(User user, ClientInfo client) {
        String refreshToken = SessionTokens.generate();
        refreshTokens.save(RefreshToken.issue(user.getId(), SessionTokens.hash(refreshToken), clock.instant(),
                jwt.refreshTokenTtl(), client.ip(), client.userAgent()));
        UUID companyId = companyOf(user);
        JwtService.IssuedToken accessToken = jwtService.issue(user.getId(), user.getRole(), companyId,
                user.isMustChangePassword(), user.getSessionVersion());
        return new AuthResult(user, companyId, accessToken, refreshToken);
    }

    /** {@code null} tant que le module Entreprises n'existe pas, ou pour un recruteur sans entreprise. */
    public UUID companyOf(User user) {
        if (user.getRole() != Role.RECRUITER) {
            return null;
        }
        RecruiterCompanyLookup lookup = companyLookup.getIfAvailable();
        return lookup == null ? null : lookup.companyOf(user.getId()).orElse(null);
    }
}
