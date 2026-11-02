package com.smartrecruit.job.application;

import com.smartrecruit.job.domain.ContractType;
import com.smartrecruit.job.domain.ExperienceLevel;
import com.smartrecruit.job.domain.Salary;
import com.smartrecruit.job.domain.WorkMode;
import com.smartrecruit.referential.api.SkillLevel;

import java.util.List;
import java.util.UUID;

/**
 * Contenu d'offre reçu du recruteur, avant vérification des références (catégorie, compétences).
 * Les deux listes de compétences restent séparées, comme dans le formulaire, pour nommer les champs en erreur.
 */
public record JobDraft(String title, String description, UUID categoryId, String city, String countryCode,
                       WorkMode workMode, ContractType contractType, ExperienceLevel experienceLevel,
                       int minYearsExperience, Salary salary, List<RequiredSkill> requiredSkills,
                       List<UUID> preferredSkills) {

    public JobDraft {
        requiredSkills = requiredSkills == null ? List.of() : List.copyOf(requiredSkills);
        preferredSkills = preferredSkills == null ? List.of() : List.copyOf(preferredSkills);
    }

    public record RequiredSkill(UUID skillId, SkillLevel minLevel) {
    }
}
