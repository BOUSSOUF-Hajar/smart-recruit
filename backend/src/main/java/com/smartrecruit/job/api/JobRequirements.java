package com.smartrecruit.job.api;

import com.smartrecruit.referential.api.SkillLevel;

import java.util.List;
import java.util.UUID;

/**
 * Exigences d'une offre, entrées du matching (analyse fonctionnelle §5.1).
 *
 * @param preferredSkillIds compétences souhaitées ; peut être vide (poids redistribués, §5.3)
 */
public record JobRequirements(UUID jobId, String title, List<RequiredSkill> requiredSkills,
                              List<UUID> preferredSkillIds, int minYearsExperience) {

    public JobRequirements {
        requiredSkills = List.copyOf(requiredSkills);
        preferredSkillIds = List.copyOf(preferredSkillIds);
    }

    public record RequiredSkill(UUID skillId, SkillLevel minLevel) {
    }
}
