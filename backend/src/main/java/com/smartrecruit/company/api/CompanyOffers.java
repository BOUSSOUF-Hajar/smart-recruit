package com.smartrecruit.company.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Offres d'une entreprise, vues depuis l'administration des entreprises (US-49).
 * <p>
 * Implémentée par le module Offres : il dépend d'Entreprises, jamais l'inverse. Sans implémentation,
 * les entreprises sont présentées sans offre.
 */
public interface CompanyOffers {

    /** Compteurs par entreprise ; une entreprise sans offre est absente. Une seule requête. */
    Map<UUID, OfferCounts> countsOf(Collection<UUID> companyIds);

    /**
     * RG-31 : clôt les offres publiées de l'entreprise, dans la transaction de l'appelant.
     * Les candidatures en cours continuent ; les candidats gardent l'accès à leur suivi.
     *
     * @return nombre d'offres clôturées
     */
    int closePublished(UUID companyId);

    /** @param total offres non archivées ; {@code published} : celles qui reçoivent des candidatures */
    record OfferCounts(long total, long published) {
    }
}
