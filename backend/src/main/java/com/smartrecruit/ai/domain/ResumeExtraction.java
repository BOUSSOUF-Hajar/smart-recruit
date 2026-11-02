package com.smartrecruit.ai.domain;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Réponse attendue du modèle pour l'analyse d'un CV (prompt {@code resume-extraction-v1}). Le schéma
 * JSON envoyé au modèle et ces contraintes disent la même chose : le modèle est contraint par le
 * schéma, puis le backend revalide (une réponse non conforme est rejetée, jamais corrigée).
 * <p>
 * Aucun champ personnel ni critère protégé (âge, sexe, origine, situation familiale) : ils ne
 * peuvent pas apparaître dans un résultat (D7.2).
 */
public record ResumeExtraction(
        @NotNull @Size(max = 80) List<@NotNull @Valid Skill> skills,
        @NotNull @Size(max = 30) List<@NotNull @Valid Experience> experiences,
        @NotNull @Size(max = 20) List<@NotNull @Valid Education> educations,
        @NotNull @Size(max = 15) List<@NotNull @Valid Language> languages,
        @NotBlank @Size(max = 600) String summary) {

    /** @param evidence extrait du CV qui justifie la compétence (garde-fou contre l'hallucination) */
    public record Skill(@NotBlank @Size(max = 80) String name, @NotBlank @Size(max = 300) String evidence) {
    }

    public record Experience(@NotBlank @Size(max = 150) String title, @Size(max = 150) String company,
                             @Min(1950) @Max(2100) Integer startYear, @Min(1950) @Max(2100) Integer endYear,
                             @Min(0) @Max(720) Integer durationMonths) {
    }

    public record Education(@NotBlank @Size(max = 150) String degree, @Size(max = 150) String school,
                            @Min(1950) @Max(2100) Integer year) {
    }

    public record Language(@NotBlank @Size(max = 40) String language, @Size(max = 20) String level) {
    }
}
