package com.smartrecruit.ai.domain.matching;

import com.smartrecruit.ai.domain.matching.MatchingInput.Preferred;
import com.smartrecruit.ai.domain.matching.MatchingInput.ProfileSkill;
import com.smartrecruit.ai.domain.matching.MatchingInput.Required;
import com.smartrecruit.ai.domain.matching.MatchingInput.ResumeSkill;
import com.smartrecruit.ai.domain.matching.MatchingResult.MatchedSkill;
import com.smartrecruit.ai.domain.matching.MatchingResult.MissingSkill;
import com.smartrecruit.ai.domain.matching.MatchingResult.Source;

import java.util.ArrayList;
import java.util.List;

/**
 * Score de matching (analyse fonctionnelle §5), calculé par le backend, jamais par le modèle :
 * <pre>
 * score = round(100 × (0,60 C_req + 0,20 C_souh + 0,20 A))      sans compétence souhaitée : 0,75 / 0,25
 * </pre>
 * <ul>
 *   <li>C_req : par compétence requise, 1 si présente au niveau demandé ou à un niveau inconnu (CV),
 *       0,5 si présente à un niveau déclaré inférieur, 0 si absente ;</li>
 *   <li>C_souh : part des compétences souhaitées présentes, niveau ignoré ;</li>
 *   <li>A : années du candidat ÷ années demandées, plafonné à 1 ; 1 si l'offre demande 0 an ;
 *       0 si les années du candidat sont inconnues.</li>
 * </ul>
 * Comparaison par identifiant du référentiel ; aucune déduction implicite (Spring Boot ne vaut pas
 * Java). Une compétence présente dans le profil et le CV compte une fois, le profil primant.
 * Classe pure : mêmes entrées, même score, testable en millisecondes (exemple chiffré : 81).
 */
public final class MatchingCalculator {

    public static final String FORMULA_VERSION = "v1";

    private MatchingCalculator() {
    }

    public static MatchingResult compute(MatchingInput input) {
        if (input.required().isEmpty()) {
            throw new IllegalArgumentException("Une offre a au moins une compétence requise (RG-20)");
        }
        List<MatchedSkill> matched = new ArrayList<>();
        List<MissingSkill> missing = new ArrayList<>();

        double requiredPoints = 0;
        for (Required skill : input.required()) {
            ProfileSkill declared = input.profileSkills().get(skill.skillId());
            ResumeSkill found = input.resumeSkills().get(skill.skillId());
            if (declared != null) {
                boolean below = declared.level() != null && skill.minLevel() != null
                        && declared.level().compareTo(skill.minLevel()) < 0;
                requiredPoints += below ? 0.5 : 1;
                matched.add(new MatchedSkill(skill.skillId(), skill.name(), Source.PROFILE, declared.level(), null,
                        true, below));
            } else if (found != null) {
                requiredPoints += 1;   // niveau inconnu : compté comme suffisant (§5.2)
                matched.add(new MatchedSkill(skill.skillId(), skill.name(), Source.RESUME, null, found.evidence(),
                        true, false));
            } else {
                missing.add(new MissingSkill(skill.skillId(), skill.name(), true));
            }
        }
        double requiredCoverage = requiredPoints / input.required().size();

        Double preferredCoverage = null;
        if (!input.preferred().isEmpty()) {
            int present = 0;
            for (Preferred skill : input.preferred()) {
                ProfileSkill declared = input.profileSkills().get(skill.skillId());
                ResumeSkill found = input.resumeSkills().get(skill.skillId());
                if (declared != null || found != null) {
                    present++;
                    matched.add(new MatchedSkill(skill.skillId(), skill.name(),
                            declared != null ? Source.PROFILE : Source.RESUME,
                            declared != null ? declared.level() : null,
                            declared != null ? null : found.evidence(), false, false));
                } else {
                    missing.add(new MissingSkill(skill.skillId(), skill.name(), false));
                }
            }
            preferredCoverage = (double) present / input.preferred().size();
        }

        boolean experienceKnown = input.candidateYears() != null;
        double experienceFit = input.minYears() <= 0 ? 1
                : experienceKnown ? Math.min(1, input.candidateYears() / input.minYears()) : 0;

        double raw = preferredCoverage == null
                ? 0.75 * requiredCoverage + 0.25 * experienceFit
                : 0.60 * requiredCoverage + 0.20 * preferredCoverage + 0.20 * experienceFit;
        int score = (int) Math.round(100 * raw);
        return new MatchingResult(score, MatchLabel.of(score), round3(requiredCoverage),
                preferredCoverage == null ? null : round3(preferredCoverage), round3(experienceFit),
                experienceKnown || input.minYears() <= 0, matched, missing, FORMULA_VERSION);
    }

    private static double round3(double value) {
        return Math.round(value * 1000) / 1000.0;
    }
}
