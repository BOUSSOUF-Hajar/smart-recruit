package com.smartrecruit.identity.infrastructure;

import com.smartrecruit.identity.domain.RefreshToken;
import com.smartrecruit.identity.domain.RevocationReason;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /**
     * Lecture verrouillée ({@code SELECT … FOR UPDATE}) : deux rafraîchissements simultanés avec le
     * même jeton sont traités l'un après l'autre, et le second voit le jeton déjà révoqué.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from RefreshToken t where t.tokenHash = :hash")
    Optional<RefreshToken> findForUpdate(@Param("hash") String tokenHash);

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revocationReason = :reason
            where t.userId = :userId and t.revokedAt is null""")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now,
                         @Param("reason") RevocationReason reason);

    @Modifying(flushAutomatically = true)
    @Query("""
            update RefreshToken t set t.revokedAt = :now, t.revocationReason = :reason
            where t.userId = :userId and t.revokedAt is null and t.id <> :keptId""")
    int revokeAllForUserExcept(@Param("userId") UUID userId, @Param("keptId") UUID keptId,
                               @Param("now") Instant now, @Param("reason") RevocationReason reason);
}
