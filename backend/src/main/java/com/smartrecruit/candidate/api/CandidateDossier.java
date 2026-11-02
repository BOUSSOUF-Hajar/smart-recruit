package com.smartrecruit.candidate.api;

import com.smartrecruit.referential.api.SkillLevel;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Profil d'un candidat tel que le voit un recruteur dans un dossier de candidature (US-31). Les listes
 * sont déjà triées comme sur l'écran du candidat (expériences et formations les plus récentes d'abord).
 * Aucun champ discriminant n'existe dans le profil (US-13), donc aucun à masquer ici.
 */
public record CandidateDossier(UUID id, String email, String firstName, String lastName, String phone, String city,
                               String countryCode, String headline, String summary, Integer yearsOfExperience,
                               List<ExperienceItem> experiences, List<EducationItem> educations,
                               List<SkillItem> skills, List<LanguageItem> languages, List<LinkItem> links) {

    public CandidateDossier {
        experiences = List.copyOf(experiences);
        educations = List.copyOf(educations);
        skills = List.copyOf(skills);
        languages = List.copyOf(languages);
        links = List.copyOf(links);
    }

    public String fullName() {
        return (firstName + " " + lastName).strip();
    }

    public record ExperienceItem(String jobTitle, String companyName, String city, LocalDate startDate,
                                 LocalDate endDate, String description) {
    }

    public record EducationItem(String degree, String school, String fieldOfStudy, Integer startYear,
                                Integer endYear) {
    }

    public record SkillItem(UUID skillId, String name, SkillLevel level, Integer years) {
    }

    /** @param level niveau CECRL (A1 à C2) ou NATIVE */
    public record LanguageItem(String languageCode, String level) {
    }

    public record LinkItem(String type, String url) {
    }
}
