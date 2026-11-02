package com.smartrecruit.demo;

import com.smartrecruit.shared.storage.FileStorage;
import org.springframework.jdbc.core.simple.JdbcClient;
import tools.jackson.databind.json.JsonMapper;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Écritures SQL des données de démonstration. Chaque méthode respecte les règles de l'application
 * que la base ne vérifie pas : une candidature a sa ligne CREATED puis une ligne par changement de
 * statut, un recruteur rattaché à son entreprise, un CV actif par candidat, etc.
 */
class DemoWriter {

    private final JdbcClient jdbc;
    private final FileStorage storage;
    private final JsonMapper json;
    private final String passwordHash;
    /** Statut courant de chaque candidature, pour remplir {@code from_status}. */
    private final Map<UUID, String> statuses = new HashMap<>();
    private final Map<UUID, UUID> resumes = new HashMap<>();

    DemoWriter(JdbcClient jdbc, FileStorage storage, JsonMapper json, String passwordHash) {
        this.jdbc = jdbc;
        this.storage = storage;
        this.json = json;
        this.passwordHash = passwordHash;
    }

    UUID user(String email, String firstName, String lastName, String role, boolean enabled, Instant at) {
        return jdbc.sql("""
                        insert into app_user (email, password_hash, first_name, last_name, role, enabled,
                                              must_change_password, privacy_accepted_at, last_login_at,
                                              created_at, updated_at, session_version)
                        values (:email, :hash, :first, :last, :role, :enabled, false, :privacy, :login, :at, :at,
                                :sessionVersion)
                        returning id""")
                .param("email", email).param("hash", passwordHash).param("first", firstName)
                .param("last", lastName).param("role", role).param("enabled", enabled)
                .param("privacy", role.equals("CANDIDATE") ? ts(at) : null)
                .param("login", enabled ? ts(at.plusSeconds(3600)) : null).param("at", ts(at))
                // Un compte désactivé a vu sa version de session augmenter (US-48)
                .param("sessionVersion", enabled ? 0 : 1)
                .query(UUID.class).single();
    }

    UUID company(String name, String sector, String size, String city, String website, String description,
                 boolean active, Instant at) {
        return jdbc.sql("""
                        insert into company (name, sector, size_range, city, country_code, website, description,
                                             active, created_at, updated_at)
                        values (:name, :sector, :size, :city, 'MA', :website, :description, :active, :at, :at)
                        returning id""")
                .param("name", name).param("sector", sector).param("size", size).param("city", city)
                .param("website", website).param("description", description).param("active", active)
                .param("at", ts(at)).query(UUID.class).single();
    }

    void recruiterProfile(UUID userId, UUID companyId, String jobTitle, Instant at) {
        jdbc.sql("""
                        insert into recruiter_profile (user_id, company_id, job_title, created_at, updated_at)
                        values (:user, :company, :title, :at, :at)""")
                .param("user", userId).param("company", companyId).param("title", jobTitle).param("at", ts(at))
                .update();
    }

    /** Compte, profil, rubriques et CV PDF actif d'un candidat fictif. */
    UUID candidate(DemoData.Candidate spec, Instant at) {
        UUID id = user(spec.email(), spec.firstName(), spec.lastName(), "CANDIDATE", true, at);
        jdbc.sql("""
                        insert into candidate_profile (user_id, phone, city, country_code, headline, summary,
                                                       years_of_experience, created_at, updated_at)
                        values (:id, :phone, :city, 'MA', :headline, :summary, :years, :at, :at)""")
                .param("id", id).param("phone", spec.phone()).param("city", spec.city())
                .param("headline", spec.headline()).param("summary", spec.summary()).param("years", spec.years())
                .param("at", ts(at)).update();
        spec.skills().forEach((skill, level) -> jdbc.sql("""
                        insert into candidate_skill (candidate_id, skill_id, level, years)
                        select :id, s.id, :level, :years from skill s where s.normalized_name = :skill""")
                .param("id", id).param("level", level).param("years", Math.max(1, spec.years() - 1))
                .param("skill", skill).update());
        for (DemoData.Experience experience : spec.experiences()) {
            jdbc.sql("""
                            insert into experience (candidate_id, job_title, company_name, city, start_date, end_date,
                                                    description)
                            values (:id, :title, :company, :city, :start, :end, :description)""")
                    .param("id", id).param("title", experience.title()).param("company", experience.company())
                    .param("city", spec.city()).param("start", experience.start()).param("end", experience.end())
                    .param("description", experience.description()).update();
        }
        jdbc.sql("""
                        insert into education (candidate_id, degree, school, field_of_study, start_year, end_year)
                        values (:id, :degree, :school, :field, :start, :end)""")
                .param("id", id).param("degree", spec.education().degree()).param("school", spec.education().school())
                .param("field", spec.education().field()).param("start", spec.education().endYear() - 3)
                .param("end", spec.education().endYear()).update();
        Map<String, String> languages = Map.of("fr", "C2", "ar", "NATIVE", "en", spec.english());
        languages.forEach((code, level) -> jdbc.sql("""
                        insert into candidate_language (candidate_id, language_code, level) values (:id, :code, :level)""")
                .param("id", id).param("code", code).param("level", level).update());
        resumes.put(id, resume(id, spec, at.plusSeconds(1800)));
        return id;
    }

    private UUID resume(UUID candidateId, DemoData.Candidate spec, Instant at) {
        byte[] pdf = DemoPdf.resume(spec.resumeLines());
        String key = storage.store(pdf, "pdf");
        return jdbc.sql("""
                        insert into resume (candidate_id, storage_key, original_filename, content_type, size_bytes,
                                            sha256, active, uploaded_at)
                        values (:candidate, :key, :filename, 'application/pdf', :size, :sha, true, :at)
                        returning id""")
                .param("candidate", candidateId).param("key", key)
                .param("filename", "CV-" + spec.lastName().replace(' ', '-') + ".pdf")
                .param("size", pdf.length).param("sha", sha256(pdf)).param("at", ts(at))
                .query(UUID.class).single();
    }

    UUID job(DemoData.Job spec, UUID companyId, UUID createdBy, Instant now) {
        Instant created = now.minus(Duration.ofDays(spec.publishedDaysAgo() + 2L));
        Instant published = spec.status().equals("DRAFT") ? null : now.minus(Duration.ofDays(spec.publishedDaysAgo()));
        Instant expires = published == null ? null : published.plus(Duration.ofDays(45));
        UUID id = jdbc.sql("""
                        insert into job_offer (company_id, category_id, created_by, title, description, city,
                                               country_code, work_mode, contract_type, experience_level,
                                               min_years_experience, salary_min, salary_max, salary_currency,
                                               salary_period, salary_visible, status, published_at, expires_at,
                                               created_at, updated_at)
                        select :company, c.id, :createdBy, :title, :description, :city, 'MA', :workMode, :contract,
                               :level, :minYears, :salaryMin, :salaryMax, 'MAD', :period, :visible, :status,
                               :published, :expires, :created, :created
                        from job_category c where c.name = :category
                        returning id""")
                .param("company", companyId).param("createdBy", createdBy).param("title", spec.title())
                .param("description", spec.description()).param("city", spec.city())
                .param("workMode", spec.workMode()).param("contract", spec.contract()).param("level", spec.level())
                .param("minYears", spec.minYears()).param("salaryMin", spec.salaryMin())
                .param("salaryMax", spec.salaryMax()).param("period", spec.salaryMin() == null ? null : "MONTHLY")
                .param("visible", spec.salaryVisible()).param("status", spec.status())
                .param("published", ts(published)).param("expires", ts(expires)).param("created", ts(created))
                .param("category", spec.category()).query(UUID.class).single();
        spec.required().forEach((skill, level) -> jobSkill(id, skill, "REQUIRED", level));
        spec.preferred().forEach(skill -> jobSkill(id, skill, "PREFERRED", null));
        return id;
    }

    private void jobSkill(UUID jobId, String skill, String requirement, String level) {
        jdbc.sql("""
                        insert into job_offer_skill (job_offer_id, skill_id, requirement, min_level)
                        select :job, s.id, :requirement, :level from skill s where s.normalized_name = :skill""")
                .param("job", jobId).param("requirement", requirement).param("level", level).param("skill", skill)
                .update();
    }

    /** Candidature envoyée avec le CV actif du candidat, et sa première ligne d'historique. */
    UUID submit(UUID candidateId, UUID jobId, Instant at) {
        UUID id = jdbc.sql("""
                        insert into application (job_offer_id, candidate_id, resume_id, status, submitted_at,
                                                 status_changed_at, created_at, updated_at)
                        values (:job, :candidate, :resume, 'SUBMITTED', :at, :at, :at, :at)
                        returning id""")
                .param("job", jobId).param("candidate", candidateId).param("resume", resumes.get(candidateId))
                .param("at", ts(at)).query(UUID.class).single();
        event(id, "CREATED", null, "SUBMITTED", candidateId, Map.of(), at);
        statuses.put(id, "SUBMITTED");
        return id;
    }

    /** Changement de statut par un recruteur (ou retrait par le candidat), avec sa ligne d'historique. */
    void move(UUID applicationId, String target, UUID actorId, Instant at, Map<String, Object> details) {
        String from = statuses.get(applicationId);
        jdbc.sql("""
                        update application set status = :status, status_changed_at = :at, updated_at = :at,
                               rejection_reason = :reason, rejection_comment = :rejectionComment,
                               withdrawal_comment = :withdrawal, version = version + 1
                        where id = :id""")
                .param("status", target).param("at", ts(at))
                .param("reason", target.equals("REJECTED") ? details.get("rejectionReason") : null)
                .param("rejectionComment", target.equals("REJECTED") ? details.get("comment") : null)
                .param("withdrawal", target.equals("WITHDRAWN") ? details.get("comment") : null)
                .param("id", applicationId).update();
        event(applicationId, "STATUS_CHANGED", from, target, actorId, details, at);
        statuses.put(applicationId, target);
    }

    /**
     * Entretien d'une candidature au statut INTERVIEW. {@code rating} non nul : entretien terminé une
     * heure après son début, avec l'avis interne.
     */
    UUID interview(UUID applicationId, UUID jobId, UUID companyId, UUID scheduledBy, List<UUID> participants,
                   Instant createdAt, Instant startsAt, String type, String rating, String feedback) {
        boolean completed = rating != null;
        Instant completedAt = completed ? startsAt.plusSeconds(3600) : null;
        UUID id = jdbc.sql("""
                        insert into interview (application_id, company_id, job_offer_id, scheduled_by, starts_at,
                                               duration_minutes, type, meeting_url, address, candidate_instructions,
                                               status, feedback_rating, feedback_comment, completed_at,
                                               created_at, updated_at)
                        values (:application, :company, :job, :by, :starts, 45, :type, :url, :address, :instructions,
                                :status, :rating, :feedback, :completedAt, :created, :updated)
                        returning id""")
                .param("application", applicationId).param("company", companyId).param("job", jobId)
                .param("by", scheduledBy).param("starts", ts(startsAt)).param("type", type)
                .param("url", type.equals("VIDEO") ? "https://meet.smartrecruit.example/demo-" + applicationId
                        .toString().substring(0, 8) : null)
                .param("address", type.equals("ON_SITE") ? "Siège, 12 rue de la Démo, Casablanca" : null)
                .param("instructions", "Prévoyez 45 minutes. Une pièce d'identité vous sera demandée à l'accueil.")
                .param("status", completed ? "COMPLETED" : "SCHEDULED").param("rating", rating)
                .param("feedback", feedback).param("completedAt", ts(completedAt)).param("created", ts(createdAt))
                .param("updated", ts(completed ? completedAt : createdAt)).query(UUID.class).single();
        participants.forEach(participant -> jdbc.sql("""
                        insert into interview_participant (interview_id, user_id) values (:id, :user)""")
                .param("id", id).param("user", participant).update());
        Map<String, Object> scheduled = new LinkedHashMap<>();
        scheduled.put("interviewId", id.toString());
        scheduled.put("startsAt", startsAt.toString());
        scheduled.put("type", type);
        event(applicationId, "INTERVIEW_SCHEDULED", null, null, scheduledBy, scheduled, createdAt);
        if (completed) {
            event(applicationId, "INTERVIEW_COMPLETED", null, null, scheduledBy,
                    Map.of("interviewId", id.toString(), "rating", rating), completedAt);
        }
        return id;
    }

    void note(UUID applicationId, UUID authorId, String content, Instant at) {
        UUID id = jdbc.sql("""
                        insert into note (application_id, author_id, content, created_at, updated_at)
                        values (:application, :author, :content, :at, :at) returning id""")
                .param("application", applicationId).param("author", authorId).param("content", content)
                .param("at", ts(at)).query(UUID.class).single();
        event(applicationId, "NOTE_ADDED", null, null, authorId, Map.of("noteId", id.toString()), at);
    }

    private void event(UUID applicationId, String type, String from, String to, UUID actorId,
                       Map<String, Object> details, Instant at) {
        jdbc.sql("""
                        insert into application_event (application_id, event_type, from_status, to_status, actor_id,
                                                       details, occurred_at)
                        values (:application, :type, :from, :to, :actor, cast(:details as jsonb), :at)""")
                .param("application", applicationId).param("type", type).param("from", from).param("to", to)
                .param("actor", actorId)
                .param("details", details.isEmpty() ? null : json.writeValueAsString(details))
                .param("at", ts(at)).update();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
