package com.smartrecruit.candidate.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Lecture des candidats par les autres modules, sans accès à leurs tables ni à leurs entités.
 * L'identifiant d'un candidat est celui de son compte.
 */
public interface CandidateDirectory {

    /** RG-09 : ce qui manque pour postuler, et le CV actif qui serait joint. */
    ApplicationReadiness readiness(UUID candidateId);

    /**
     * Profil complet pour le dossier de candidature du recruteur (US-31).
     *
     * @throws com.smartrecruit.shared.domain.ResourceNotFoundException si le candidat n'existe pas
     */
    CandidateDossier dossier(UUID candidateId);

    /** US-45 : complétude du profil et CV actif. */
    ProfileOverview overview(UUID candidateId);

    /** Une version de CV, active ou non (une candidature garde la sienne, RG-12). */
    Optional<ResumeSummary> resume(UUID resumeId);

    /**
     * Fichier d'une version de CV, pour un téléchargement déjà autorisé par l'appelant.
     *
     * @throws com.smartrecruit.shared.domain.ResourceNotFoundException si la version ou son fichier manque
     */
    ResumeContent resumeContent(UUID resumeId);
}
