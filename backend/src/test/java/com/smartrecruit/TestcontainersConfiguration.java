package com.smartrecruit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * PostgreSQL éphémère pour les tests d'intégration.
 * <p>
 * {@code @ServiceConnection} remplace la configuration {@code spring.datasource.*} par celle du conteneur :
 * un test ne peut donc jamais toucher la base de développement. Sans Docker, le test échoue au lieu de
 * se rabattre sur une autre base.
 * <p>
 * Usage : {@code @Import(TestcontainersConfiguration.class)} sur une classe de test {@code @SpringBootTest}.
 * <p>
 * Sans Docker, les tests peuvent viser une base PostgreSQL 18 déjà lancée :
 * {@code ./mvnw verify -Dsmartrecruit.test.external-database=true
 * -Dspring.datasource.url=jdbc:postgresql://localhost:5432/smartrecruit_it
 * -Dspring.datasource.username=postgres -Dspring.datasource.password=postgres}.
 * La base doit être vide au premier lancement : Flyway y applique les migrations.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /** Même version majeure qu'en production (docker-compose). */
    static final DockerImageName POSTGRES_IMAGE = DockerImageName.parse("postgres:18-alpine");

    @Bean
    @ServiceConnection
    @ConditionalOnProperty(name = "smartrecruit.test.external-database", havingValue = "false", matchIfMissing = true)
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
