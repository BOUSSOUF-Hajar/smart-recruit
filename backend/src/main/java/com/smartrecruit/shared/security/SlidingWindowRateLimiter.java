package com.smartrecruit.shared.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Limiteur à fenêtre glissante, en mémoire : au plus {@code maxEvents} événements par clé
 * sur la durée {@code window}.
 * <p>
 * Chaque lecture ou écriture d'une clé passe par {@code compute} : la {@link ConcurrentHashMap}
 * l'exécute de façon atomique pour cette clé, sans verrou global.
 * <p>
 * Suffisant pour une seule instance de l'API. Avec plusieurs instances, le compteur devra être
 * partagé (base ou cache), sans changer les appelants (architecture §9).
 */
public class SlidingWindowRateLimiter {

    private final int maxEvents;
    private final Duration window;
    private final Clock clock;
    private final ConcurrentHashMap<String, Deque<Instant>> events = new ConcurrentHashMap<>();

    public SlidingWindowRateLimiter(int maxEvents, Duration window, Clock clock) {
        this.maxEvents = maxEvents;
        this.window = window;
        this.clock = clock;
    }

    /** Si la clé a atteint la limite, renvoie le délai avant qu'une nouvelle tentative soit possible. */
    public Optional<Duration> blockedFor(String key) {
        Instant now = clock.instant();
        AtomicReference<Duration> wait = new AtomicReference<>();
        events.computeIfPresent(key, (k, deque) -> {
            Deque<Instant> recent = prune(deque, now);
            if (recent != null && recent.size() >= maxEvents) {
                wait.set(Duration.between(now, recent.peekFirst().plus(window)));
            }
            return recent;
        });
        return Optional.ofNullable(wait.get());
    }

    public void record(String key) {
        Instant now = clock.instant();
        events.compute(key, (k, deque) -> {
            Deque<Instant> recent = deque == null ? null : prune(deque, now);
            Deque<Instant> target = recent == null ? new ArrayDeque<>() : recent;
            target.addLast(now);
            return target;
        });
    }

    public void reset(String key) {
        events.remove(key);
    }

    /** Retire les événements sortis de la fenêtre ; renvoie {@code null} (clé supprimée) s'il n'en reste aucun. */
    private Deque<Instant> prune(Deque<Instant> deque, Instant now) {
        Instant limit = now.minus(window);
        while (!deque.isEmpty() && !deque.peekFirst().isAfter(limit)) {
            deque.pollFirst();
        }
        return deque.isEmpty() ? null : deque;
    }
}
