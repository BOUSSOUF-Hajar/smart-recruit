package com.smartrecruit.candidate.web;

import com.smartrecruit.candidate.domain.LanguageLevel;
import com.smartrecruit.candidate.domain.LinkType;
import com.smartrecruit.referential.api.SkillLevel;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Réponses du profil candidat, au format de {@code docs/api.md} §4. */
final class ProfileResponses {

    private ProfileResponses() {
    }

    record ProfileResponse(String firstName, String lastName, String email, String phone, LocationResponse location,
                           String headline, String summary, Integer yearsOfExperience,
                           List<ExperienceResponse> experiences, List<EducationResponse> educations,
                           List<SkillResponse> skills, List<LanguageResponse> languages, List<LinkResponse> links,
                           ResumeResponse resume, CompletenessResponse completeness, long version) {
    }

    record LocationResponse(String city, String countryCode) {
    }

    record ExperienceResponse(UUID id, String jobTitle, String companyName, String city, LocalDate startDate,
                              LocalDate endDate, boolean current, String description) {
    }

    record EducationResponse(UUID id, String degree, String school, String fieldOfStudy, Integer startYear,
                             Integer endYear) {
    }

    record SkillResponse(UUID skillId, String name, SkillLevel level, Integer years) {
    }

    record LanguageResponse(String languageCode, LanguageLevel level) {
    }

    record LinkResponse(LinkType type, String url) {
    }

    /** Jamais la clé de stockage ni l'empreinte : seulement ce que le candidat a besoin de voir. */
    record ResumeResponse(UUID id, String originalFilename, long sizeBytes, Instant uploadedAt) {
    }

    record CompletenessResponse(int percent, List<String> missing) {
    }
}
