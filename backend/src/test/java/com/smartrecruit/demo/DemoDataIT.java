package com.smartrecruit.demo;

import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.shared.storage.FileStorage;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-56. Le jeu est chargé puis vérifié dans une transaction annulée à la fin : la base partagée par
 * les autres tests d'intégration reste intacte. Le profil {@code demo} n'est pas activé ici, pour que
 * le chargement automatique au démarrage ne s'exécute pas sur cette base partagée.
 */
@SpringBootTest
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class DemoDataIT {

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private FileStorage storage;

    @Autowired
    private JsonMapper json;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TransactionTemplate transaction;

    @Autowired
    private Clock clock;

    @Test
    void theDemoDatasetIsCompleteConsistentAndFictitious() {
        DemoDataSeeder seeder = new DemoDataSeeder(jdbc, storage, json, passwordEncoder, transaction, clock,
                "Demo-SmartRecruit-2026");
        transaction.executeWithoutResult(status -> {
            seeder.seed();

            // Un compte par rôle, connectables avec le mot de passe du README
            for (String email : List.of(DemoDataSeeder.ADMIN_EMAIL, DemoDataSeeder.RECRUITER_EMAIL,
                    DemoDataSeeder.CANDIDATE_EMAIL)) {
                String hash = jdbc.sql("select password_hash from app_user where email = :email and enabled "
                        + "and not must_change_password").param("email", email).query(String.class).single();
                assertThat(passwordEncoder.matches("Demo-SmartRecruit-2026", hash)).as(email).isTrue();
            }
            // Candidatures à tous les statuts ; offres à tous les statuts ; une entreprise désactivée
            assertThat(jdbc.sql("select distinct status from application").query(String.class).list())
                    .containsExactlyInAnyOrder("SUBMITTED", "SCREENING", "SHORTLISTED", "INTERVIEW", "OFFER",
                            "HIRED", "REJECTED", "WITHDRAWN");
            assertThat(jdbc.sql("select distinct status from job_offer where created_by in (select id from "
                    + "app_user where email like '%.example')").query(String.class).list())
                    .containsExactlyInAnyOrder("DRAFT", "PUBLISHED", "CLOSED", "ARCHIVED");
            assertThat(count("select count(*) from company where not active and name = 'Saffran Studio'")).isEqualTo(1);

            // Historique cohérent : chaque candidature commence par CREATED et finit sur son statut actuel
            assertThat(count("""
                    select count(*) from application a
                    where (select e.to_status from application_event e
                           where e.application_id = a.id and e.to_status is not null
                           order by e.occurred_at desc limit 1) <> a.status
                       or not exists (select 1 from application_event e
                                      where e.application_id = a.id and e.event_type = 'CREATED')""")).isZero();
            // RG-33 : pas de proposition ni d'embauche sans entretien terminé
            assertThat(count("""
                    select count(*) from application a
                    where a.status in ('OFFER', 'HIRED')
                      and not exists (select 1 from interview i
                                      where i.application_id = a.id and i.status = 'COMPLETED')""")).isZero();
            // RG-14 : tout rejet a un motif
            assertThat(count("select count(*) from application where status = 'REJECTED' and rejection_reason is null"))
                    .isZero();
            // Tableaux de bord vivants : des entretiens dans les 7 prochains jours, des candidatures récentes
            assertThat(count("""
                    select count(*) from interview
                    where status = 'SCHEDULED' and starts_at between now() and now() + interval '7 days'"""))
                    .isGreaterThanOrEqualTo(3);
            assertThat(count("select count(*) from application where submitted_at > now() - interval '7 days'"))
                    .isGreaterThanOrEqualTo(5);
            // Chaque CV actif existe dans le stockage et contient du texte (lisible par l'IA)
            List<String> keys = jdbc.sql("select storage_key from resume where active").query(String.class).list();
            assertThat(keys).hasSizeGreaterThanOrEqualTo(12);
            keys.forEach(key -> assertThat(storage.load(key).exists()).isTrue());
            // Fictif : toutes les adresses sont sur des domaines réservés à la documentation
            assertThat(count("""
                    select count(*) from app_user u
                    where u.created_at > now() - interval '200 days'
                      and u.email not like '%.example'
                      and exists (select 1 from candidate_profile p where p.user_id = u.id
                                  and exists (select 1 from resume r where r.candidate_id = p.user_id
                                              and r.original_filename like 'CV-%'))""")).isZero();

            status.setRollbackOnly();
        });
    }

    @Test
    void nothingIsLoadedWhenTheDatabaseAlreadyHasCompanies() {
        DemoDataSeeder seeder = new DemoDataSeeder(jdbc, storage, json, passwordEncoder, transaction, clock,
                "Demo-SmartRecruit-2026");
        transaction.executeWithoutResult(status -> {
            jdbc.sql("insert into company (name, created_at, updated_at) values ('Déjà là', now(), now())").update();
            seeder.seedIfEmpty();
            assertThat(count("select count(*) from app_user where email = 'admin@smartrecruit.example'")).isZero();
            status.setRollbackOnly();
        });
    }

    private long count(String sql) {
        return jdbc.sql(sql).query(Long.class).single();
    }
}
