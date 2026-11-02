package com.smartrecruit.shared;

import com.smartrecruit.TestcontainersConfiguration;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-02 : en profil prod, Hibernate valide le schéma sans jamais le modifier.
 * <p>
 * Les variables obligatoires du profil prod sont fournies ici ; la connexion à la base
 * est remplacée par le conteneur grâce à {@code @ServiceConnection}.
 */
@SpringBootTest(properties = {
        "PUBLIC_URL=https://smartrecruit.example",
        "DB_URL=jdbc:postgresql://unused:5432/unused",
        "DB_USERNAME=unused",
        "DB_PASSWORD=unused",
        "JWT_SECRET=prod-profile-test-secret-0123456789abcdefghij",
        "STORAGE_ROOT=target/prod-profile-it/resumes"
})
@ActiveProfiles("prod")
@Import(TestcontainersConfiguration.class)
class ProdProfileSchemaIT {

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Test
    void hibernateOnlyValidatesTheSchema() {
        assertThat(entityManagerFactory.getProperties())
                .containsEntry("hibernate.hbm2ddl.auto", "validate");
    }
}
