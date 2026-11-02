package com.smartrecruit.shared;

import com.smartrecruit.TestcontainersConfiguration;
import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationInfo;
import org.flywaydb.core.api.MigrationState;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.util.Arrays;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-02 : le schéma est créé par Flyway, sur un vrai PostgreSQL 18 éphémère.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DatabaseMigrationIT {

    @Autowired
    private Flyway flyway;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void allMigrationsAreAppliedInOrderAndRecorded() {
        MigrationInfo[] applied = flyway.info().applied();

        assertThat(applied).isNotEmpty();
        assertThat(applied).allSatisfy(m -> assertThat(m.getState()).isEqualTo(MigrationState.SUCCESS));
        assertThat(flyway.info().pending()).isEmpty();

        // Ordre strictement croissant des versions
        assertThat(Arrays.stream(applied).map(MigrationInfo::getVersion).toList()).isSorted();

        // L'historique est bien enregistré dans la table de Flyway
        Integer rows = jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class);
        assertThat(rows).isEqualTo(applied.length);
    }

    @Test
    void runsOnPostgresql18() {
        Integer major = jdbc.queryForObject(
                "select current_setting('server_version_num')::int / 10000", Integer.class);
        assertThat(major).isEqualTo(18);
    }

    @Test
    void uuidv7IsAvailableNatively() {
        UUID id = jdbc.queryForObject("select uuidv7()", UUID.class);
        assertThat(id).isNotNull();
        assertThat(id.version()).isEqualTo(7);
    }

    @Test
    void unaccentExtensionIsInstalled() {
        String result = jdbc.queryForObject("select unaccent('Développeuse Été')", String.class);
        assertThat(result).isEqualTo("Developpeuse Ete");
    }
}
