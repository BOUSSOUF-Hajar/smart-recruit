package com.smartrecruit.job.api;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lecture des offres par le module Candidatures, sans accès à leurs tables ni à leurs entités. */
public interface JobCatalog {

    /** Offre quel que soit son statut ; vide si elle n'existe pas. */
    Optional<JobReference> find(UUID jobId);

    /** Offres trouvées ; les identifiants inconnus sont absents de la map. */
    Map<UUID, JobReference> findAll(Collection<UUID> jobIds);

    /** Compétences et expérience demandées, pour le matching (US-42). */
    Optional<JobRequirements> requirements(UUID jobId);
}
