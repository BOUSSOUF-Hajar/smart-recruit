package com.smartrecruit.application.api;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Le statut d'une candidature a changé (US-29, US-32, US-33). Publié dans la transaction du
 * changement : les écouteurs synchrones (historique ; annulation des entretiens en phase 7, RG-22)
 * réussissent ou échouent avec lui.
 *
 * @param actorId recruteur ou candidat à l'origine du changement
 * @param details informations à conserver dans l'historique (motif de rejet, commentaire) ;
 *                jamais exposées au candidat
 */
public record ApplicationStatusChanged(UUID applicationId, UUID jobOfferId, UUID candidateId,
                                       ApplicationStatus from, ApplicationStatus to, UUID actorId,
                                       Map<String, Object> details, Instant occurredAt) {

    public ApplicationStatusChanged {
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    /** RG-22 : ces statuts annulent les entretiens planifiés. */
    public boolean endsTheProcess() {
        return to == ApplicationStatus.REJECTED || to == ApplicationStatus.WITHDRAWN;
    }
}
