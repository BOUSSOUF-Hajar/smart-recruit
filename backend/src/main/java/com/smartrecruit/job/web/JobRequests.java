package com.smartrecruit.job.web;

import com.smartrecruit.job.application.JobDraft;
import com.smartrecruit.job.domain.ContractType;
import com.smartrecruit.job.domain.ExperienceLevel;
import com.smartrecruit.job.domain.Salary;
import com.smartrecruit.job.domain.SalaryPeriod;
import com.smartrecruit.job.domain.WorkMode;
import com.smartrecruit.referential.api.SkillLevel;
import com.smartrecruit.shared.validation.ChronologicalOrder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Corps des écritures sur les offres ({@code docs/api.md} §6). Aucun champ entreprise : il vient du token. */
final class JobRequests {

    static final int MAX_SKILLS = 30;

    private JobRequests() {
    }

    record LocationRequest(
            @NotBlank @Size(max = 80) String city,
            @NotNull @Pattern(regexp = "[A-Za-z]{2}", message = "doit être un code pays ISO à 2 lettres") String countryCode) {
    }

    @ChronologicalOrder(start = "min", end = "max", message = "ne peut pas être inférieur au minimum")
    record SalaryRequest(
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal min,
            @DecimalMin("0") @Digits(integer = 10, fraction = 2) BigDecimal max,
            @Pattern(regexp = "[A-Za-z]{3}", message = "doit être un code devise ISO 4217") String currency,
            SalaryPeriod period,
            boolean visible) {

        Salary toSalary() {
            return new Salary(min, max, currency, period, visible);
        }
    }

    record RequiredSkillRequest(@NotNull UUID skillId, @NotNull SkillLevel minLevel) {
    }

    record PreferredSkillRequest(@NotNull UUID skillId) {
    }

    /**
     * POST /jobs et PUT /jobs/{id}. Un champ {@code companyId} envoyé par le client est ignoré :
     * Jackson ne le lie à rien (US-21).
     *
     * @param version obligatoire en modification, ignorée à la création
     */
    record JobRequest(
            @NotBlank @Size(max = 150) String title,
            @NotBlank @Size(min = 50, max = 10_000) String description,
            @NotNull UUID categoryId,
            @NotNull @Valid LocationRequest location,
            @NotNull WorkMode workMode,
            @NotNull ContractType contractType,
            @NotNull ExperienceLevel experienceLevel,
            @Min(0) @Max(30) Integer minYearsExperience,
            @Valid SalaryRequest salary,
            @NotNull @Size(max = MAX_SKILLS) List<@NotNull @Valid RequiredSkillRequest> requiredSkills,
            @Size(max = MAX_SKILLS) List<@NotNull @Valid PreferredSkillRequest> preferredSkills,
            Long version) {

        JobDraft toDraft() {
            return new JobDraft(title, description, categoryId, location.city(), location.countryCode(), workMode,
                    contractType, experienceLevel, minYearsExperience == null ? 0 : minYearsExperience,
                    salary == null ? null : salary.toSalary(),
                    requiredSkills.stream().map(s -> new JobDraft.RequiredSkill(s.skillId(), s.minLevel())).toList(),
                    preferredSkills == null ? List.of()
                            : preferredSkills.stream().map(PreferredSkillRequest::skillId).toList());
        }
    }

    /** Publication et réouverture : date d'expiration, la fin de journée étant calculée dans le fuseau de l'entreprise. */
    record ExpiryRequest(@NotNull LocalDate expiryDate) {
    }
}
