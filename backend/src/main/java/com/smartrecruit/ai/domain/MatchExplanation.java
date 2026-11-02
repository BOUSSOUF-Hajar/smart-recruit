package com.smartrecruit.ai.domain;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Partie rédigée par le modèle (prompt {@code match-explain-v1}). Il n'y a pas de champ « score » :
 * si le modèle en renvoie un, il est ignoré à la lecture ; seul le calcul du backend est affiché.
 */
public record MatchExplanation(
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 300) String> strengths,
        @NotNull @Size(max = 5) List<@NotBlank @Size(max = 300) String> weaknesses,
        @NotBlank @Size(max = 600) String summary) {
}
