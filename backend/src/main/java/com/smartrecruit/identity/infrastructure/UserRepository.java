package com.smartrecruit.identity.infrastructure;

import com.smartrecruit.identity.domain.User;
import com.smartrecruit.shared.security.Role;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UserRepository extends JpaRepository<User, UUID>, JpaSpecificationExecutor<User> {

    /** L'email est stocké normalisé : l'appelant passe {@link User#normalizeEmail}. */
    Optional<User> findByEmail(String normalizedEmail);

    boolean existsByEmail(String normalizedEmail);

    boolean existsByRole(Role role);

    /**
     * RG-29 : admins actifs, verrouillés ({@code SELECT … FOR UPDATE}). Deux admins qui se désactivent
     * mutuellement au même instant sont traités l'un après l'autre : le second voit qu'il ne reste qu'un admin.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.role = com.smartrecruit.shared.security.Role.ADMIN and u.enabled = true")
    List<User> lockActiveAdmins();

    /** Comptes dont des tokens ont été révoqués : seuls eux ont besoin d'un contrôle de version. */
    @Query("select u.id as id, u.sessionVersion as version from User u where u.sessionVersion > 0")
    List<SessionVersion> findRevokedSessionVersions();

    interface SessionVersion {
        UUID getId();

        int getVersion();
    }
}
