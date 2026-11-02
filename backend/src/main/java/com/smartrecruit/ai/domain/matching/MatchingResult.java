package com.smartrecruit.ai.domain.matching;

import com.smartrecruit.referential.api.SkillLevel;

import java.util.List;
import java.util.UUID;

/**
 * Résultat du calcul : score, décomposition et compétences, prêts à être affichés et expliqués.
 *
 * @param preferredCoverage {@code null} si l'offre n'a aucune compétence souhaitée
 * @param experienceKnown   faux si les années du candidat sont inconnues (A = 0, sans rien inventer)
 */
public record MatchingResult(int score, MatchLabel label, double requiredCoverage, Double preferredCoverage,
                             double experienceFit, boolean experienceKnown, List<MatchedSkill> matched,
                             List<MissingSkill> missing, String formulaVersion) {

    public enum Source { PROFILE, RESUME }

    /**
     * @param level              niveau déclaré dans le profil ; {@code null} pour une compétence trouvée dans le CV
     * @param evidence           extrait du CV, pour une compétence trouvée dans le CV
     * @param belowRequiredLevel présente, mais sous le niveau demandé (compte pour 0,5)
     */
    public record MatchedSkill(UUID skillId, String name, Source source, SkillLevel level, String evidence,
                               boolean required, boolean belowRequiredLevel) {
    }

    public record MissingSkill(UUID skillId, String name, boolean required) {
    }
}
