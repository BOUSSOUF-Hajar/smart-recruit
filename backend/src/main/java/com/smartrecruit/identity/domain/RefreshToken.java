package com.smartrecruit.identity.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Une session : un refresh token, dont seule l'empreinte SHA-256 est stockée (ENF-SEC-05).
 * Une fuite de la table ne permet donc pas d'ouvrir une session.
 * <p>
 * Pas de {@code version} : une ligne n'est jamais modifiée qu'une fois (révocation), sous verrou.
 * La cause de la révocation distingue une rotation (dont le rejeu signale un vol) d'une fermeture normale.
 */
@Entity
@Table(name = "refresh_token")
public class RefreshToken {

    static final int USER_AGENT_MAX_LENGTH = 255;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    /** Simple identifiant plutôt qu'une association : la session n'a jamais besoin de charger le compte. */
    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    /** {@code CHAR(64)} en base : sans ce type explicite, la validation du schéma attendrait un VARCHAR. */
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "token_hash", nullable = false, updatable = false, length = 64)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "revocation_reason", length = 16)
    private RevocationReason revocationReason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @JdbcTypeCode(SqlTypes.INET)
    @Column(name = "created_ip", updatable = false)
    private String createdIp;

    @Column(name = "user_agent", updatable = false, length = USER_AGENT_MAX_LENGTH)
    private String userAgent;

    protected RefreshToken() {
        // JPA
    }

    public static RefreshToken issue(UUID userId, String tokenHash, Instant now, Duration ttl,
                                     String ip, String userAgent) {
        RefreshToken token = new RefreshToken();
        token.userId = Objects.requireNonNull(userId, "userId");
        token.tokenHash = Objects.requireNonNull(tokenHash, "tokenHash");
        token.createdAt = now;
        token.expiresAt = now.plus(ttl);
        token.createdIp = ip;
        token.userAgent = userAgent == null || userAgent.length() <= USER_AGENT_MAX_LENGTH
                ? userAgent
                : userAgent.substring(0, USER_AGENT_MAX_LENGTH);
        return token;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }

    public void revoke(Instant now, RevocationReason reason) {
        if (revokedAt == null) {
            revokedAt = now;
            revocationReason = reason;
        }
    }

    /** Déjà remplacé par rotation : le présenter à nouveau signifie qu'il a été copié. */
    public boolean wasRotated() {
        return revocationReason == RevocationReason.ROTATED;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public RevocationReason getRevocationReason() {
        return revocationReason;
    }
}
