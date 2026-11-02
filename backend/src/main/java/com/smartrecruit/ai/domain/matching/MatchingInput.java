package com.smartrecruit.ai.domain.matching;

import com.smartrecruit.referential.api.SkillLevel;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Entrées du calcul (analyse fonctionnelle §5.1), déjà rattachées au référentiel de compétences.
 *
 * @param profileSkills  compétences déclarées dans le profil, avec leur niveau : source prioritaire
 * @param resumeSkills   compétences extraites du CV par l'IA, niveau inconnu, avec l'extrait justificatif
 * @param candidateYears années d'expérience ; {@code null} si inconnues (A = 0, mention affichée)
 */
public record MatchingInput(List<Required> required, List<Preferred> preferred, int minYears,
                            Map<UUID, ProfileSkill> profileSkills, Map<UUID, ResumeSkill> resumeSkills,
                            Double candidateYears) {

    public MatchingInput {
        required = List.copyOf(required);
        preferred = List.copyOf(preferred);
        profileSkills = Map.copyOf(profileSkills);
        resumeSkills = Map.copyOf(resumeSkills);
    }

    public record Required(UUID skillId, String name, SkillLevel minLevel) {
    }

    public record Preferred(UUID skillId, String name) {
    }

    public record ProfileSkill(String name, SkillLevel level) {
    }

    public record ResumeSkill(String name, String evidence) {
    }
}
