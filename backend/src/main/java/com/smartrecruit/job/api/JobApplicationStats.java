package com.smartrecruit.job.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Chiffres de candidatures dont les offres ont besoin, fournis par le module Candidatures (phase 6).
 * Inversion de dépendance : Candidatures dépend d'Offres, jamais l'inverse. Tant qu'aucune
 * implémentation n'existe, les offres considèrent qu'elles n'ont reçu aucune candidature.
 */
public interface JobApplicationStats {

    /** Nombre de candidatures par statut, pour chaque offre demandée. */
    Map<UUID, Map<String, Long>> countsByStatus(Collection<UUID> jobIds);

    boolean hasApplications(UUID jobId);

    /** Candidature de ce candidat à cette offre, pour remplacer « Postuler » par un lien (US-26). */
    Optional<UUID> applicationOf(UUID jobId, UUID candidateId);
}
