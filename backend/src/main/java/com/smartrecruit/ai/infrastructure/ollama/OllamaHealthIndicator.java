package com.smartrecruit.ai.infrastructure.ollama;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import tools.jackson.databind.JsonNode;

/**
 * État d'Ollama dans le health check (US-40, D7.3). L'IA est facultative (RG-28) : Ollama arrêté ne
 * rend pas l'application « DOWN ». Le composant répond {@code UNKNOWN} (de priorité inférieure à
 * {@code UP} dans l'agrégation) avec {@code available: false} et la raison.
 */
public class OllamaHealthIndicator implements HealthIndicator {

    private final RestClient client;
    private final String model;

    public OllamaHealthIndicator(RestClient client, String model) {
        this.client = client;
        this.model = model;
    }

    @Override
    public Health health() {
        try {
            JsonNode tags = client.get().uri("/api/tags").retrieve().body(JsonNode.class);
            boolean installed = false;
            if (tags != null) {
                for (JsonNode installedModel : tags.path("models")) {
                    installed |= model.equals(installedModel.path("name").asString(""));
                }
            }
            return installed
                    ? Health.up().withDetail("available", true).withDetail("model", model).build()
                    : Health.unknown().withDetail("available", false).withDetail("model", model)
                            .withDetail("reason", "modèle non installé (ollama pull " + model + ")").build();
        } catch (RestClientException e) {
            return Health.unknown().withDetail("available", false).withDetail("model", model)
                    .withDetail("reason", "Ollama injoignable").build();
        }
    }
}
