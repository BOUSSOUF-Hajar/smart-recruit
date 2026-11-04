package com.smartrecruit.ai.application;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.net.URI;
import java.time.Duration;

/**
 * Configuration de l'assistance IA (D7.3). {@code enabled=false} : endpoints en {@code AI_DISABLED},
 * boutons masqués, reste de l'application intact (RG-28).
 *
 * @param provider       {@code ollama} en développement et en production, {@code fake} pour les tests
 * @param maxInputChars  texte de CV tronqué au-delà (les CV utiles tiennent largement dans cette limite)
 * @param requestsPerMinute demandes IA par recruteur et par minute (docs/api.md §9)
 */
@Validated
@ConfigurationProperties(prefix = "smartrecruit.ai")
public record AiProperties(
        @DefaultValue("false") boolean enabled,
        @DefaultValue("ollama") @NotBlank String provider,
        @DefaultValue("12000") @Min(1000) @Max(100000) int maxInputChars,
        @DefaultValue("10") @Min(1) int requestsPerMinute,
        @Valid @NotNull @DefaultValue Ollama ollama,
        @Valid @NotNull @DefaultValue Executor executor) {

    /**
     * @param model       modèle épinglé (nom et étiquette exacts), choisi par le banc d'essai
     *                    (docs/ai-model-benchmark.md)
     * @param readTimeout au-delà, l'appel échoue ; plus large que l'objectif de 120 s (ENF-PERF-05) pour
     *                    qu'un processeur plus lent donne une analyse lente plutôt qu'un échec
     * @param think       {@code false} coupe le raisonnement des modèles qui en ont un (qwen3.5) : avec lui,
     *                    aucun CV n'aboutit en 300 s sur CPU. Vide : champ non envoyé, choix du modèle
     */
    public record Ollama(
            @DefaultValue("http://localhost:11434") @NotNull URI baseUrl,
            @DefaultValue("qwen3.5:4b") @NotBlank String model,
            @DefaultValue("5s") @NotNull Duration connectTimeout,
            @DefaultValue("180s") @NotNull Duration readTimeout,
            @DefaultValue("false") Boolean think) {
    }

    /** Sur CPU, deux analyses simultanées se ralentissent déjà l'une l'autre. */
    public record Executor(@DefaultValue("2") @Min(1) @Max(8) int threads,
                           @DefaultValue("50") @Min(1) int queueCapacity) {
    }
}
