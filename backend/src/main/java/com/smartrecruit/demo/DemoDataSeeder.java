package com.smartrecruit.demo;

import com.smartrecruit.shared.storage.FileStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * US-56 : remplit une base vide avec une plateforme vivante — 4 entreprises (dont une désactivée),
 * 5 recruteurs, 12 candidats avec CV PDF, 10 offres à tous les statuts, 22 candidatures
 * à tous les statuts avec leur historique, des entretiens passés et à venir, des notes.
 * <p>
 * Les dates sont relatives au démarrage : les tableaux de bord (7 jours, 30 jours, entretiens à venir)
 * ont toujours quelque chose à montrer. Rien n'est fait si une entreprise existe déjà : redémarrer ne
 * duplique rien, et une base de production n'est jamais touchée même avec le profil activé par erreur.
 */
@Component
@Profile("demo")
class DemoDataSeeder {

    static final String ADMIN_EMAIL = "admin@smartrecruit.example";
    static final String RECRUITER_EMAIL = "recruteur@atlasia.example";
    static final String CANDIDATE_EMAIL = "candidat@demo.example";

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    private final JdbcClient jdbc;
    private final FileStorage storage;
    private final JsonMapper json;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate transaction;
    private final Clock clock;
    private final String password;

    DemoDataSeeder(JdbcClient jdbc, FileStorage storage, JsonMapper json, PasswordEncoder passwordEncoder,
                   TransactionTemplate transaction, Clock clock,
                   @Value("${smartrecruit.demo.password:Demo-SmartRecruit-2026}") String password) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.json = json;
        this.passwordEncoder = passwordEncoder;
        this.transaction = transaction;
        this.clock = clock;
        this.password = password;
    }

    @EventListener(ApplicationReadyEvent.class)
    void seedIfEmpty() {
        Integer companies = jdbc.sql("select count(*) from company").query(Integer.class).single();
        if (companies > 0) {
            log.info("Données de démonstration : base déjà remplie, rien à faire");
            return;
        }
        transaction.executeWithoutResult(status -> seed());
        log.info("Données de démonstration chargées. Comptes : {} / {} / {} (mot de passe commun configuré par "
                + "smartrecruit.demo.password)", ADMIN_EMAIL, RECRUITER_EMAIL, CANDIDATE_EMAIL);
    }

    /** Une seule transaction : en cas d'erreur, la base reste vide et le prochain démarrage réessaie. */
    void seed() {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MINUTES);
        DemoWriter db = new DemoWriter(jdbc, storage, json, passwordEncoder.encode(password));
        Instant longAgo = ago(now, 120, 0);

        db.user(ADMIN_EMAIL, "Amina", "Admin", "ADMIN", true, longAgo);

        Map<String, UUID> companies = new HashMap<>();
        companies.put("atlasia", db.company("Atlasia Tech", "Édition de logiciels", "51-200", "Casablanca",
                "https://atlasia.example", "Éditeur fictif d'une plateforme de facturation pour les PME.", true, longAgo));
        companies.put("novaterra", db.company("Novaterra Conseil", "Conseil en transformation digitale", "11-50",
                "Rabat", "https://novaterra.example", "Cabinet de conseil fictif.", true, longAgo));
        companies.put("riad", db.company("Riad Logistique", "Transport et logistique", "201-1000", "Tanger",
                "https://riad-logistique.example", "Logisticien fictif du nord du Maroc.", true, longAgo));
        companies.put("saffran", db.company("Saffran Studio", "Design", "1-10", "Marrakech", null,
                "Studio fictif, désactivé pour montrer la cascade RG-31.", false, longAgo));

        UUID salma = recruiter(db, RECRUITER_EMAIL, "Salma", "Berrada", "Chargée de recrutement",
                companies.get("atlasia"), true, longAgo);
        UUID yassine = recruiter(db, "yassine.tahiri@atlasia.example", "Yassine", "Tahiri", "Lead technique",
                companies.get("atlasia"), true, longAgo);
        UUID leila = recruiter(db, "recruteur@novaterra.example", "Leila", "Chraibi", "Responsable RH",
                companies.get("novaterra"), true, longAgo);
        UUID omar = recruiter(db, "recruteur@riad-logistique.example", "Omar", "Fassi", "Responsable recrutement",
                companies.get("riad"), true, longAgo);
        UUID hamza = recruiter(db, "ancien@saffran.example", "Hamza", "Kettani", "Fondateur",
                companies.get("saffran"), false, longAgo);
        Map<String, UUID> authors = Map.of("atlasia", salma, "novaterra", leila, "riad", omar, "saffran", hamza);

        Map<String, UUID> c = new HashMap<>();
        for (DemoData.Candidate candidate : DemoData.CANDIDATES) {
            c.put(candidate.key(), db.candidate(candidate, ago(now, 100, 0)));
        }
        Map<String, UUID> j = new HashMap<>();
        Map<String, UUID> companyOf = new HashMap<>();
        for (DemoData.Job job : DemoData.JOBS) {
            UUID id = db.job(job, companies.get(job.company()), authors.get(job.company()), now);
            j.put(job.key(), id);
            companyOf.put(job.key(), companies.get(job.company()));
        }

        Flow flow = new Flow(db, now, j, companyOf);

        // --- Développeur·se Java : le parcours complet, côté recruteur et côté candidat de démonstration ---
        UUID yasmineJava = flow.submit(c.get("yasmine"), "java", 16);
        flow.move(yasmineJava, "SCREENING", salma, 14).move(yasmineJava, "SHORTLISTED", salma, 10)
                .move(yasmineJava, "INTERVIEW", salma, 7);
        flow.interview(yasmineJava, "java", salma, List.of(salma, yassine), 7, inDays(now, 2, 10), "VIDEO", null);
        db.note(yasmineJava, yassine, "Très bon échange technique au téléphone, à confirmer en entretien.",
                ago(now, 8, 15));

        UUID saraJava = flow.submit(c.get("sara"), "java", 18);
        flow.move(saraJava, "SCREENING", salma, 17).move(saraJava, "SHORTLISTED", salma, 15)
                .move(saraJava, "INTERVIEW", salma, 12);
        flow.interview(saraJava, "java", salma, List.of(salma, yassine), 12, ago(now, 5, 10), "ON_SITE", "POSITIVE");
        flow.move(saraJava, "OFFER", salma, 3);
        db.note(saraJava, yassine, "Profil senior solide, pourrait encadrer l'équipe paiements.", ago(now, 5, 12));
        db.note(saraJava, salma, "Prétentions dans la fourchette ; disponible sous un mois.", ago(now, 4, 9));

        UUID rachidJava = flow.submit(c.get("rachid"), "java", 13);
        flow.move(rachidJava, "SCREENING", salma, 12).move(rachidJava, "REJECTED", salma, 11,
                Map.of("rejectionReason", "INSUFFICIENT_EXPERIENCE", "comment", "Trop junior pour ce poste."));
        flow.submit(c.get("nora"), "java", 2);
        UUID mehdiJava = flow.submit(c.get("mehdi"), "java", 6);
        flow.move(mehdiJava, "SCREENING", salma, 5);
        UUID ilyasJava = flow.submit(c.get("ilyas"), "java", 9);
        flow.move(ilyasJava, "WITHDRAWN", c.get("ilyas"), 8,
                Map.of("comment", "Je préfère d'abord terminer mes études."));

        // --- Développeur·se Angular : une embauche ---
        UUID mehdiAngular = flow.submit(c.get("mehdi"), "angular", 11);
        flow.move(mehdiAngular, "SCREENING", salma, 10).move(mehdiAngular, "SHORTLISTED", salma, 9)
                .move(mehdiAngular, "INTERVIEW", salma, 8);
        flow.interview(mehdiAngular, "angular", salma, List.of(salma), 8, ago(now, 6, 14), "VIDEO", "POSITIVE");
        flow.move(mehdiAngular, "OFFER", salma, 5).move(mehdiAngular, "HIRED", salma, 3);
        UUID noraAngular = flow.submit(c.get("nora"), "angular", 7);
        flow.move(noraAngular, "SCREENING", salma, 6).move(noraAngular, "SHORTLISTED", salma, 4);
        flow.submit(c.get("yasmine"), "angular", 1);

        // --- Ingénieur·e DevOps ---
        UUID anasDevops = flow.submit(c.get("anas"), "devops", 5);
        flow.move(anasDevops, "SCREENING", yassine, 4).move(anasDevops, "INTERVIEW", yassine, 3);
        flow.interview(anasDevops, "devops", yassine, List.of(yassine), 3, inDays(now, 4, 14), "ON_SITE", null);
        db.note(anasDevops, yassine, "Expérience Kubernetes en production : à creuser en entretien.", ago(now, 3, 16));
        UUID saraDevops = flow.submit(c.get("sara"), "devops", 4);
        flow.move(saraDevops, "SCREENING", yassine, 2);

        // --- Stage (offre clôturée) ---
        UUID rachidStage = flow.submit(c.get("rachid"), "stage", 55);
        flow.move(rachidStage, "SCREENING", salma, 52).move(rachidStage, "INTERVIEW", salma, 48);
        flow.interview(rachidStage, "stage", salma, List.of(salma), 48, ago(now, 45, 10), "ON_SITE", "POSITIVE");
        flow.move(rachidStage, "OFFER", salma, 43).move(rachidStage, "HIRED", salma, 40);
        UUID ilyasStage = flow.submit(c.get("ilyas"), "stage", 54);
        flow.move(ilyasStage, "REJECTED", salma, 41, Map.of("rejectionReason", "POSITION_FILLED"));

        // --- Novaterra Conseil ---
        UUID imaneConsultant = flow.submit(c.get("imane"), "consultant", 10);
        flow.move(imaneConsultant, "SCREENING", leila, 9).move(imaneConsultant, "INTERVIEW", leila, 6);
        flow.interview(imaneConsultant, "consultant", leila, List.of(leila), 6, inDays(now, 1, 11), "PHONE", null);
        db.note(imaneConsultant, leila, "Excellente communication, références ERP solides.", ago(now, 6, 17));
        UUID ayoubConsultant = flow.submit(c.get("ayoub"), "consultant", 12);
        flow.move(ayoubConsultant, "REJECTED", leila, 10,
                Map.of("rejectionReason", "PROFILE_MISMATCH", "comment", "Profil plutôt pilotage que fonctionnel."));
        flow.submit(c.get("khadija"), "consultant", 3);
        UUID ayoubChef = flow.submit(c.get("ayoub"), "chef-projet", 8);
        flow.move(ayoubChef, "SCREENING", leila, 7).move(ayoubChef, "SHORTLISTED", leila, 5);
        flow.submit(c.get("imane"), "chef-projet", 2);

        // --- Riad Logistique ---
        UUID youssefEntrepot = flow.submit(c.get("youssef"), "entrepot", 15);
        flow.move(youssefEntrepot, "SCREENING", omar, 13).move(youssefEntrepot, "INTERVIEW", omar, 10);
        flow.interview(youssefEntrepot, "entrepot", omar, List.of(omar), 10, ago(now, 2, 9), "ON_SITE", "NEUTRAL");
        UUID hajarEntrepot = flow.submit(c.get("hajar"), "entrepot", 6);
        flow.move(hajarEntrepot, "SCREENING", omar, 4);
        UUID hajarSupply = flow.submit(c.get("hajar"), "supply", 85);
        flow.move(hajarSupply, "REJECTED", omar, 70, Map.of("rejectionReason", "POSITION_FILLED"));

        // --- Saffran Studio (désactivée) : la candidature reste visible par la candidate ---
        flow.submit(c.get("khadija"), "designer", 35);
    }

    private static UUID recruiter(DemoWriter db, String email, String firstName, String lastName, String title,
                                  UUID companyId, boolean enabled, Instant at) {
        UUID id = db.user(email, firstName, lastName, "RECRUITER", enabled, at);
        db.recruiterProfile(id, companyId, title, at);
        return id;
    }

    /** Il y a {@code days} jours, à {@code hour} heures (UTC) : les événements d'une journée restent ordonnés. */
    static Instant ago(Instant now, int days, int hour) {
        return now.truncatedTo(ChronoUnit.DAYS).minus(Duration.ofDays(days)).plus(Duration.ofHours(hour));
    }

    static Instant inDays(Instant now, int days, int hour) {
        return now.truncatedTo(ChronoUnit.DAYS).plus(Duration.ofDays(days)).plus(Duration.ofHours(hour));
    }

    /** Raccourcis lisibles pour écrire les parcours de candidature ci-dessus. */
    private record Flow(DemoWriter db, Instant now, Map<String, UUID> jobs, Map<String, UUID> companyOf) {

        UUID submit(UUID candidateId, String job, int daysAgo) {
            return db.submit(candidateId, jobs.get(job), ago(now, daysAgo, 8));
        }

        Flow move(UUID applicationId, String target, UUID actor, int daysAgo) {
            return move(applicationId, target, actor, daysAgo, Map.of());
        }

        Flow move(UUID applicationId, String target, UUID actor, int daysAgo, Map<String, Object> details) {
            // Après la candidature (8 h) et avant l'entretien du même jour : 9 h
            db.move(applicationId, target, actor, ago(now, daysAgo, 9), details);
            return this;
        }

        void interview(UUID applicationId, String job, UUID scheduledBy, List<UUID> participants, int createdDaysAgo,
                       Instant startsAt, String type, String rating) {
            String feedback = rating == null ? null
                    : rating.equals("POSITIVE") ? "Très bonne maîtrise technique et bonne communication."
                    : "Expérience solide mais peu d'exemples concrets sur l'amélioration continue.";
            db.interview(applicationId, jobs.get(job), companyOf.get(job), scheduledBy, participants,
                    ago(now, createdDaysAgo, 10), startsAt, type, rating, feedback);
        }
    }
}
