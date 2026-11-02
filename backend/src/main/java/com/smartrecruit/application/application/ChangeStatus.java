package com.smartrecruit.application.application;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.domain.RejectionReason;

/**
 * Demande de changement de statut par un recruteur (US-32, US-33).
 *
 * @param rejectionReason obligatoire si la cible est REJECTED, ignoré sinon
 * @param comment         commentaire interne facultatif
 * @param version         version lue par le recruteur (verrouillage optimiste)
 */
public record ChangeStatus(ApplicationStatus target, RejectionReason rejectionReason, String comment, long version) {
}
