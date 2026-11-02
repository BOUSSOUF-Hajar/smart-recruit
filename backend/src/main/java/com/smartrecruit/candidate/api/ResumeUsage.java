package com.smartrecruit.candidate.api;

import java.util.UUID;

/**
 * US-19 : un CV joint à une candidature est seulement désactivé, jamais effacé (RG-12). Implémenté
 * par le module Candidatures, qui dépend des Candidats et non l'inverse ; sans implémentation,
 * aucun CV n'est considéré comme joint.
 */
public interface ResumeUsage {

    boolean isAttached(UUID resumeId);
}
