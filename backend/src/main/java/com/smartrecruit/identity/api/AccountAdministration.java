package com.smartrecruit.identity.api;

import java.util.Collection;
import java.util.UUID;

/**
 * Actions d'administration sur les comptes, ouvertes aux autres modules : la désactivation d'une
 * entreprise désactive ses recruteurs dans la même transaction (RG-31).
 */
public interface AccountAdministration {

    /**
     * Désactive les comptes encore actifs, révoque leurs sessions et audite chaque désactivation.
     * Rejoint la transaction de l'appelant.
     *
     * @param reason motif inscrit dans le journal d'audit (ex. {@code COMPANY_DEACTIVATED})
     * @return nombre de comptes désactivés
     */
    int disableAll(Collection<UUID> userIds, UUID actorId, String reason);
}
