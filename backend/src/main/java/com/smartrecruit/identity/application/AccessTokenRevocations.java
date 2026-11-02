package com.smartrecruit.identity.application;

import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.UserRepository;
import com.smartrecruit.shared.security.RevokedAccessTokens;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Versions de session en mémoire (US-48) : le contrôle de chaque requête ne lit pas la base.
 * <p>
 * Chargées au démarrage pour les seuls comptes déjà révoqués au moins une fois, puis tenues à jour
 * <strong>après la validation</strong> de la transaction qui révoque : une désactivation annulée ne
 * coupe personne. Limite assumée : avec plusieurs instances du backend, chacune ne voit que ses propres
 * révocations jusqu'à son redémarrage ; il faudrait alors un canal commun (Redis, LISTEN/NOTIFY).
 */
@Component
class AccessTokenRevocations implements RevokedAccessTokens {

    private final Map<UUID, Integer> versions = new ConcurrentHashMap<>();

    AccessTokenRevocations(UserRepository users) {
        users.findRevokedSessionVersions().forEach(row -> versions.put(row.getId(), row.getVersion()));
    }

    @Override
    public boolean isRevoked(UUID userId, int sessionVersion) {
        return sessionVersion < versions.getOrDefault(userId, 0);
    }

    /** À appeler après {@link User#revokeSessions()}, dans la transaction qui l'a modifié. */
    void register(User user) {
        UUID id = user.getId();
        int version = user.getSessionVersion();
        Runnable apply = () -> versions.merge(id, version, Math::max);
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    apply.run();
                }
            });
        } else {
            apply.run();
        }
    }
}
