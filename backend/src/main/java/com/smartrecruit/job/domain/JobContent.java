package com.smartrecruit.job.domain;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/** Contenu modifiable d'une offre, déjà validé (forme par la couche web, références par le service). */
public record JobContent(String title, String description, UUID categoryId, String city, String countryCode,
                         WorkMode workMode, ContractType contractType, ExperienceLevel experienceLevel,
                         int minYearsExperience, Salary salary, List<JobSkill> skills) {

    public JobContent {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        Objects.requireNonNull(categoryId, "categoryId");
        Objects.requireNonNull(city, "city");
        Objects.requireNonNull(countryCode, "countryCode");
        Objects.requireNonNull(workMode, "workMode");
        Objects.requireNonNull(contractType, "contractType");
        Objects.requireNonNull(experienceLevel, "experienceLevel");
        salary = salary == null ? Salary.none() : salary;
        skills = List.copyOf(skills);
    }
}
