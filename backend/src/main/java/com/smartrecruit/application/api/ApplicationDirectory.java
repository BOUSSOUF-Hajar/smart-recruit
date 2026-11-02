package com.smartrecruit.application.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lecture des candidatures par le module Entretiens, sans accès à leurs tables ni à leurs entités. */
public interface ApplicationDirectory {

    Optional<ApplicationRef> find(UUID applicationId);

    /** Candidatures trouvées, en un nombre fixe de requêtes ; les identifiants inconnus sont absents. */
    Map<UUID, ApplicationRef> findAll(Collection<UUID> applicationIds);

    /** Identifiants des candidatures d'un candidat (« mes entretiens », US-38). */
    List<UUID> idsOfCandidate(UUID candidateId);
}
