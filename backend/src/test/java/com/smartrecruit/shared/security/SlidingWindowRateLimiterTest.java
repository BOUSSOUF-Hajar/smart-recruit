package com.smartrecruit.shared.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/** ENF-SEC-09 : au plus N événements par clé sur une fenêtre glissante. */
class SlidingWindowRateLimiterTest {

    private MutableClock clock;
    private SlidingWindowRateLimiter limiter;

    @BeforeEach
    void setUp() {
        clock = new MutableClock(Instant.parse("2026-10-19T10:00:00Z"));
        limiter = new SlidingWindowRateLimiter(5, Duration.ofMinutes(15), clock);
    }

    @Test
    void allowsUpToTheLimit() {
        for (int i = 0; i < 4; i++) {
            limiter.record("salma@example.com");
        }
        assertThat(limiter.blockedFor("salma@example.com")).isEmpty();
    }

    @Test
    void blocksOnceTheLimitIsReachedAndSaysForHowLong() {
        for (int i = 0; i < 5; i++) {
            limiter.record("salma@example.com");
            clock.advance(Duration.ofMinutes(1));
        }
        // Premier échec à 10:00, maintenant 10:05 : libre à 10:15, donc dans 10 minutes
        assertThat(limiter.blockedFor("salma@example.com")).contains(Duration.ofMinutes(10));
    }

    @Test
    void oldEventsLeaveTheWindow() {
        for (int i = 0; i < 5; i++) {
            limiter.record("salma@example.com");
        }
        clock.advance(Duration.ofMinutes(15));
        assertThat(limiter.blockedFor("salma@example.com")).isEmpty();
    }

    @Test
    void keysAreIndependent() {
        for (int i = 0; i < 5; i++) {
            limiter.record("salma@example.com");
        }
        assertThat(limiter.blockedFor("youssef@example.com")).isEmpty();
    }

    @Test
    void resetClearsTheCounter() {
        for (int i = 0; i < 5; i++) {
            limiter.record("salma@example.com");
        }
        limiter.reset("salma@example.com");
        assertThat(limiter.blockedFor("salma@example.com")).isEmpty();
    }
}
