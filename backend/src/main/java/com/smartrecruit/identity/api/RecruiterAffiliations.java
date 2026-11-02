package com.smartrecruit.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Rattachement des recruteurs à leur entreprise, vu depuis l'administration des comptes (US-48).
 * <p>
 * Implémentée par le module Entreprises, propriétaire des profils recruteurs : Identité affiche et
 * filtre les comptes par entreprise sans dépendre de lui (même inversion que {@link RecruiterCompanyLookup}).
 */
public interface RecruiterAffiliations {

    /** Rattachements trouvés ; un identifiant sans profil recruteur est absent. Requêtes en nombre fixe. */
    Map<UUID, Affiliation> of(Collection<UUID> recruiterIds);

    /** Recruteurs d'une entreprise (filtre de la liste des comptes). */
    Set<UUID> recruitersOf(UUID companyId);

    /**
     * Change l'entreprise ou le poste d'un recruteur, dans la transaction de l'appelant.
     *
     * @return vrai si l'entreprise a changé : ses sessions doivent alors être révoquées
     * @throws com.smartrecruit.shared.domain.FieldValidationException si l'entreprise est inconnue
     * @throws com.smartrecruit.shared.domain.InvalidStateException     si elle est désactivée
     */
    boolean update(UUID recruiterId, UUID companyId, String jobTitle);

    record Affiliation(UUID companyId, String companyName, boolean companyActive, String jobTitle) {
    }
}
