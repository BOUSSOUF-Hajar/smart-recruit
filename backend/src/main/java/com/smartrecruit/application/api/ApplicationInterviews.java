package com.smartrecruit.application.api;

import java.util.List;
import java.util.UUID;

/**
 * Entretiens d'une candidature, pour le dossier (US-31) et la vue du candidat (US-28). Implémenté par
 * le module Entretiens ; sans implémentation, une candidature n'a aucun entretien.
 */
public interface ApplicationInterviews {

    /** Du plus ancien au plus récent. */
    List<InterviewBrief> of(UUID applicationId);
}
