package com.smartrecruit.ai.domain;

import java.util.List;
import java.util.UUID;

/**
 * Analyse d'un CV telle qu'elle est enregistrée (colonne JSONB {@code resume_analysis.result}) :
 * l'extraction du modèle, avec chaque compétence rattachée au référentiel quand c'est possible.
 */
public record AnalysisDocument(List<AnalyzedSkill> skills, List<ResumeExtraction.Experience> experiences,
                               List<ResumeExtraction.Education> educations,
                               List<ResumeExtraction.Language> languages, String summary) {

    /**
     * @param skillId       compétence du référentiel, ou {@code null} si le nom est inconnu (affichée, non comptée)
     * @param referenceName nom dans le référentiel
     */
    public record AnalyzedSkill(String name, String evidence, UUID skillId, String referenceName) {
    }
}
