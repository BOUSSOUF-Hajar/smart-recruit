package com.smartrecruit.job.application;

import com.smartrecruit.job.domain.JobContent;
import com.smartrecruit.job.domain.JobSkill;
import com.smartrecruit.referential.api.CategoryCatalog;
import com.smartrecruit.referential.api.SkillCatalog;
import com.smartrecruit.referential.api.SkillSummary;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.FieldValidationException.FieldError;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;

/**
 * Vérifie les références d'une offre dans les autres modules (US-21) : catégorie active, compétences
 * connues et actives, et chaque compétence dans une seule des deux listes. Toutes les erreurs sont
 * renvoyées ensemble, chacune sur son champ.
 */
@Component
class JobContentValidator {

    private final CategoryCatalog categories;
    private final SkillCatalog skills;

    JobContentValidator(CategoryCatalog categories, SkillCatalog skills) {
        this.categories = categories;
        this.skills = skills;
    }

    JobContent validate(JobDraft draft) {
        List<FieldError> errors = new ArrayList<>();
        boolean categoryActive = categories.findCategory(draft.categoryId()).map(c -> c.active()).orElse(false);
        if (!categoryActive) {
            errors.add(new FieldError("categoryId", "Catégorie inconnue ou désactivée."));
        }

        Map<UUID, SkillSummary> known = skills.findByIds(Stream.concat(
                draft.requiredSkills().stream().map(JobDraft.RequiredSkill::skillId),
                draft.preferredSkills().stream()).toList());
        Set<UUID> seen = new HashSet<>();
        List<JobSkill> jobSkills = new ArrayList<>();
        for (int i = 0; i < draft.requiredSkills().size(); i++) {
            JobDraft.RequiredSkill skill = draft.requiredSkills().get(i);
            if (check("requiredSkills[" + i + "].skillId", skill.skillId(), known, seen, errors)) {
                jobSkills.add(JobSkill.required(skill.skillId(), skill.minLevel()));
            }
        }
        for (int i = 0; i < draft.preferredSkills().size(); i++) {
            UUID skillId = draft.preferredSkills().get(i);
            if (check("preferredSkills[" + i + "].skillId", skillId, known, seen, errors)) {
                jobSkills.add(JobSkill.preferred(skillId));
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        return new JobContent(draft.title(), draft.description(), draft.categoryId(), draft.city(),
                draft.countryCode(), draft.workMode(), draft.contractType(), draft.experienceLevel(),
                draft.minYearsExperience(), draft.salary(), jobSkills);
    }

    private static boolean check(String field, UUID skillId, Map<UUID, SkillSummary> known, Set<UUID> seen,
                                 List<FieldError> errors) {
        SkillSummary skill = known.get(skillId);
        if (skill == null || !skill.active()) {
            errors.add(new FieldError(field, "Compétence inconnue ou désactivée."));
            return false;
        }
        if (!seen.add(skillId)) {
            // Requise et souhaitée à la fois, ou deux fois dans la même liste
            errors.add(new FieldError(field, "Compétence déjà présente dans l'offre."));
            return false;
        }
        return true;
    }
}
