package com.smartrecruit.identity.application;

import com.smartrecruit.identity.domain.User;
import com.smartrecruit.shared.domain.RateLimitExceededException;
import com.smartrecruit.shared.security.SecurityProperties;
import com.smartrecruit.shared.security.SlidingWindowRateLimiter;
import org.springframework.stereotype.Component;

import java.time.Clock;

/**
 * Limites de débit de l'authentification (ENF-SEC-09) :
 * <ul>
 *   <li>connexion : 5 échecs par email sur 15 minutes, compteur remis à zéro par une connexion réussie ;</li>
 *   <li>inscription : 10 par adresse IP et par heure.</li>
 * </ul>
 * Le compteur de connexion est par email et non par IP : il protège un compte précis contre
 * le test de mots de passe, même réparti sur plusieurs adresses.
 */
@Component
public class AuthRateLimits {

    private final SlidingWindowRateLimiter loginFailures;
    private final SlidingWindowRateLimiter registrations;

    public AuthRateLimits(SecurityProperties properties, Clock clock) {
        SecurityProperties.RateLimit limits = properties.rateLimit();
        this.loginFailures = new SlidingWindowRateLimiter(limits.loginFailures(), limits.loginWindow(), clock);
        this.registrations = new SlidingWindowRateLimiter(limits.registrationsPerIp(), limits.registrationWindow(), clock);
    }

    void checkLogin(String email) {
        loginFailures.blockedFor(key(email)).ifPresent(wait -> {
            throw new RateLimitExceededException(wait);
        });
    }

    void loginFailed(String email) {
        loginFailures.record(key(email));
    }

    void loginSucceeded(String email) {
        loginFailures.reset(key(email));
    }

    void checkAndRecordRegistration(String ip) {
        String key = ip == null ? "unknown" : ip;
        registrations.blockedFor(key).ifPresent(wait -> {
            throw new RateLimitExceededException(wait);
        });
        registrations.record(key);
    }

    private static String key(String email) {
        return User.normalizeEmail(email);
    }
}
