package com.smartrecruit.job.web;

import com.smartrecruit.job.domain.ContractType;
import com.smartrecruit.job.domain.ExperienceLevel;
import com.smartrecruit.job.domain.JobStatus;
import com.smartrecruit.job.domain.SalaryPeriod;
import com.smartrecruit.job.domain.WorkMode;
import com.smartrecruit.referential.api.SkillLevel;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Réponses des offres : vue publique (liste, détail) et vue recruteur, distinctes par construction. */
final class JobResponses {

    private JobResponses() {
    }

    record Ref(UUID id, String name) {
    }

    record LocationResponse(String city, String countryCode) {
    }

    /** Salaire public : jamais le drapeau {@code visible}, absent si le recruteur ne l'a pas rendu public. */
    record PublicSalary(BigDecimal min, BigDecimal max, String currency, SalaryPeriod period) {
    }

    record JobCardResponse(UUID id, String title, Ref company, LocationResponse location, WorkMode workMode,
                           ContractType contractType, ExperienceLevel experienceLevel, PublicSalary salary,
                           List<String> topSkills, Instant publishedAt, Instant expiresAt) {
    }

    record RequiredSkillResponse(UUID id, String name, SkillLevel minLevel) {
    }

    record JobDetailResponse(UUID id, String title, String description, Ref company, Ref category,
                             LocationResponse location, WorkMode workMode, ContractType contractType,
                             ExperienceLevel experienceLevel, int minYearsExperience, PublicSalary salary,
                             List<RequiredSkillResponse> requiredSkills, List<Ref> preferredSkills,
                             Instant publishedAt, Instant expiresAt, Boolean alreadyApplied,
                             UUID myApplicationId) {
    }

    record RecruiterSalary(BigDecimal min, BigDecimal max, String currency, SalaryPeriod period, boolean visible) {
    }

    record RecruiterSkillResponse(UUID skillId, String name, SkillLevel minLevel) {
    }

    record RecruiterJobResponse(UUID id, String title, String description, UUID categoryId, LocationResponse location,
                                WorkMode workMode, ContractType contractType, ExperienceLevel experienceLevel,
                                int minYearsExperience, RecruiterSalary salary,
                                List<RecruiterSkillResponse> requiredSkills,
                                List<RecruiterSkillResponse> preferredSkills, JobStatus status, Instant publishedAt,
                                Instant expiresAt, boolean expired, Ref createdBy,
                                Map<String, Long> applicationCounts, long version) {
    }

    record RecruiterJobSummaryResponse(UUID id, String title, JobStatus status, String city,
                                       ContractType contractType, Instant publishedAt, Instant expiresAt,
                                       boolean expired, Map<String, Long> applicationCounts, Instant updatedAt) {
    }
}
