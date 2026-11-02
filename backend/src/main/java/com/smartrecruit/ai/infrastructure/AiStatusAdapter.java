package com.smartrecruit.ai.infrastructure;

import com.smartrecruit.ai.api.AiStatus;
import com.smartrecruit.ai.application.AIService;
import com.smartrecruit.ai.application.AiProperties;
import com.smartrecruit.ai.infrastructure.ollama.OllamaHealthIndicator;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

/**
 * État de l'IA pour le tableau de bord admin, à partir de l'indicateur de santé d'Ollama. Le résultat
 * est gardé 30 secondes : recharger le tableau de bord ne sonde pas Ollama à chaque fois. Avec un
 * fournisseur sans indicateur (la fausse IA des tests), l'IA activée est considérée disponible.
 */
@Component
class AiStatusAdapter implements AiStatus {

    static final Duration CACHE = Duration.ofSeconds(30);

    private final AiProperties properties;
    private final AIService service;
    private final ObjectProvider<OllamaHealthIndicator> health;
    private final Clock clock;
    private volatile Snapshot cached;
    private volatile Instant cachedAt = Instant.MIN;

    AiStatusAdapter(AiProperties properties, AIService service, ObjectProvider<OllamaHealthIndicator> health,
                    Clock clock) {
        this.properties = properties;
        this.service = service;
        this.health = health;
        this.clock = clock;
    }

    @Override
    public Snapshot current() {
        Instant now = clock.instant();
        Snapshot snapshot = cached;
        if (snapshot != null && cachedAt.plus(CACHE).isAfter(now)) {
            return snapshot;
        }
        snapshot = probe();
        cached = snapshot;
        cachedAt = now;
        return snapshot;
    }

    private Snapshot probe() {
        if (!properties.enabled()) {
            return new Snapshot(State.DISABLED, null);
        }
        OllamaHealthIndicator indicator = health.getIfAvailable();
        boolean up = indicator == null || isUp(indicator.health());
        return new Snapshot(up ? State.AVAILABLE : State.UNAVAILABLE, service.modelName());
    }

    private static boolean isUp(Health health) {
        return health != null && Status.UP.equals(health.getStatus());
    }
}
