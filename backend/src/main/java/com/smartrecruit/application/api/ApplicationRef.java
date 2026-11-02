package com.smartrecruit.application.api;

import java.util.UUID;

/**
 * Ce que les autres modules (Entretiens, IA) savent d'une candidature.
 *
 * @param resumeId version du CV figée à l'envoi (RG-12), celle qu'analyse l'IA
 */
public record ApplicationRef(UUID id, UUID candidateId, String candidateName, UUID jobOfferId, String jobTitle,
                             UUID companyId, ApplicationStatus status, UUID resumeId) {
}
