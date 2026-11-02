package com.smartrecruit.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Horloge unique de l'application, en UTC (RG-32).
 * Injectée partout où l'heure compte, et remplacée par une horloge fixe dans les tests.
 */
@Configuration(proxyBeanMethods = false)
public class ClockConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
