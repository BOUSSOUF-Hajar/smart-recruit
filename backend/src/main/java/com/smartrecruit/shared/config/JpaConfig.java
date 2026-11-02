package com.smartrecruit.shared.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.auditing.DateTimeProvider;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.time.Clock;
import java.util.Optional;

/**
 * Remplissage automatique de {@code createdAt}/{@code updatedAt} (voir
 * {@link com.smartrecruit.shared.persistence.TimestampedEntity}), à partir de l'horloge de
 * l'application : les tests qui fixent l'horloge obtiennent des dates prévisibles.
 * <p>
 * Classe séparée de {@code SmartRecruitApplication} : sur la classe principale,
 * {@code @EnableJpaAuditing} casserait les tests {@code @WebMvcTest}, qui n'ont pas de JPA.
 */
@Configuration(proxyBeanMethods = false)
@EnableJpaAuditing(dateTimeProviderRef = "auditingDateTimeProvider")
public class JpaConfig {

    @Bean
    DateTimeProvider auditingDateTimeProvider(Clock clock) {
        return () -> Optional.of(clock.instant());
    }
}
