package com.smartrecruit.candidate.web;

import com.smartrecruit.candidate.application.ProfileCommands;
import com.smartrecruit.candidate.domain.EducationDetails;
import com.smartrecruit.candidate.domain.ExperienceDetails;
import com.smartrecruit.candidate.domain.LanguageLevel;
import com.smartrecruit.candidate.domain.LinkType;
import com.smartrecruit.candidate.domain.ProfileRules;
import com.smartrecruit.referential.api.SkillLevel;
import com.smartrecruit.shared.validation.ChronologicalOrder;
import com.smartrecruit.shared.validation.HttpsUrl;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Corps des écritures du profil ({@code docs/api.md} §4). Validation de forme ; les règles sont dans le domaine. */
final class ProfileRequests {

    /** Format international E.164 : « + », indicatif, numéro ; 15 chiffres au plus. */
    static final String E164 = "\\+[1-9]\\d{6,14}";

    private ProfileRequests() {
    }

    record LocationRequest(
            @Size(max = 80) String city,
            @Pattern(regexp = "[A-Za-z]{2}", message = "doit être un code pays ISO à 2 lettres") String countryCode) {
    }

    /** US-13 : aucun champ âge, photo, sexe, nationalité ou situation familiale. */
    record UpdateProfileRequest(
            @NotBlank @Size(max = 80) String firstName,
            @NotBlank @Size(max = 80) String lastName,
            @Pattern(regexp = E164, message = "doit être au format international, par exemple +212600000000")
            String phone,
            @Valid LocationRequest location,
            @Size(max = 120) String headline,
            @Size(max = 2000) String summary,
            @Min(0) @Max(50) Integer yearsOfExperience,
            @NotNull Long version) {

        ProfileCommands.UpdateBasics toCommand() {
            return new ProfileCommands.UpdateBasics(firstName, lastName, phone,
                    location == null ? null : location.city(), location == null ? null : location.countryCode(),
                    headline, summary, yearsOfExperience, version);
        }
    }

    @ChronologicalOrder(start = "startDate", end = "endDate")
    record ExperienceRequest(
            @NotBlank @Size(max = 120) String jobTitle,
            @NotBlank @Size(max = 120) String companyName,
            @Size(max = 80) String city,
            @NotNull @PastOrPresent LocalDate startDate,
            LocalDate endDate,
            @Size(max = 2000) String description) {

        ExperienceDetails toDetails() {
            return new ExperienceDetails(jobTitle, companyName, city, startDate, endDate, description);
        }
    }

    @ChronologicalOrder(start = "startYear", end = "endYear")
    record EducationRequest(
            @NotBlank @Size(max = 120) String degree,
            @NotBlank @Size(max = 120) String school,
            @Size(max = 120) String fieldOfStudy,
            @Min(1950) @Max(2100) Integer startYear,
            @Min(1950) @Max(2100) Integer endYear) {

        EducationDetails toDetails() {
            return new EducationDetails(degree, school, fieldOfStudy, startYear, endYear);
        }
    }

    record SkillEntryRequest(@NotNull UUID skillId, @NotNull SkillLevel level, @Min(0) @Max(50) Integer years) {
    }

    /**
     * Liste enveloppée dans un objet plutôt qu'un tableau JSON nu : les erreurs gardent le chemin
     * de l'élément fautif ({@code skills[3].level}), et le format peut s'enrichir sans rupture.
     */
    record ReplaceSkillsRequest(@NotNull @Size(max = ProfileRules.MAX_SKILLS) List<@NotNull @Valid SkillEntryRequest> skills) {

        List<ProfileCommands.SkillEntry> toEntries() {
            return skills.stream().map(s -> new ProfileCommands.SkillEntry(s.skillId(), s.level(), s.years())).toList();
        }
    }

    record LanguageEntryRequest(
            @NotNull @Pattern(regexp = "[a-z]{2,3}", message = "doit être un code ISO 639 en minuscules") String languageCode,
            @NotNull LanguageLevel level) {
    }

    record ReplaceLanguagesRequest(
            @NotNull @Size(max = ProfileRules.MAX_LANGUAGES) List<@NotNull @Valid LanguageEntryRequest> languages) {

        List<ProfileCommands.LanguageEntry> toEntries() {
            return languages.stream().map(l -> new ProfileCommands.LanguageEntry(l.languageCode(), l.level())).toList();
        }
    }

    record LinkEntryRequest(@NotNull LinkType type, @NotBlank @Size(max = 255) @HttpsUrl String url) {
    }

    record ReplaceLinksRequest(@NotNull @Size(max = ProfileRules.MAX_LINKS) List<@NotNull @Valid LinkEntryRequest> links) {

        List<ProfileCommands.LinkEntry> toEntries() {
            return links.stream().map(l -> new ProfileCommands.LinkEntry(l.type(), l.url())).toList();
        }
    }
}
