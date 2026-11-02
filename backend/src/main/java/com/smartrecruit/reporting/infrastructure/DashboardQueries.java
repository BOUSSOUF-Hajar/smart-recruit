package com.smartrecruit.reporting.infrastructure;

import com.smartrecruit.shared.security.Role;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;

/**
 * Requêtes d'agrégation des tableaux de bord (cahier des charges D8, analyse fonctionnelle §6.3) :
 * calculées à la volée, jamais stockées, en un nombre fixe de requêtes.
 * <p>
 * Comme la liste des candidatures (US-30), ce module de lecture interroge directement les tables
 * des autres modules : c'est son rôle (« Requêtes de lecture pour les indicateurs »). Il n'écrit
 * jamais et chaque requête est bornée au périmètre de l'appelant (entreprise ou candidat).
 * Les périodes sont glissantes (7 et 30 derniers jours), donc indépendantes du fuseau.
 */
@Repository
public class DashboardQueries {

    private static final Duration WEEK = Duration.ofDays(7);
    private static final Duration MONTH = Duration.ofDays(30);

    private final JdbcClient jdbc;

    public DashboardQueries(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    // ---------------------------------------------------------------- recruteur (US-44)

    public Map<String, Long> jobsByStatus(UUID companyId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.sql("SELECT status, count(*) FROM job_offer WHERE company_id = :company GROUP BY status")
                .param("company", companyId)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getLong(2))).list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    /** Reçues sur 7 et 30 jours (date de dépôt) ; en SCREENING et en SHORTLISTED (statut actuel). */
    public ApplicationCounts applicationCounts(UUID companyId, Instant now) {
        return jdbc.sql("""
                        SELECT count(*) FILTER (WHERE a.submitted_at >= :week)  AS last7,
                               count(*) FILTER (WHERE a.submitted_at >= :month) AS last30,
                               count(*) FILTER (WHERE a.status = 'SCREENING')   AS screening,
                               count(*) FILTER (WHERE a.status = 'SHORTLISTED') AS shortlisted
                        FROM application a JOIN job_offer j ON j.id = a.job_offer_id
                        WHERE j.company_id = :company""")
                .param("company", companyId)
                .param("week", Timestamp.from(now.minus(WEEK)))
                .param("month", Timestamp.from(now.minus(MONTH)))
                .query((rs, n) -> new ApplicationCounts(rs.getLong("last7"), rs.getLong("last30"),
                        rs.getLong("screening"), rs.getLong("shortlisted")))
                .single();
    }

    /** Embauches : passages à HIRED lus dans l'historique, pas dans le statut actuel (§6.3). */
    public long hiresSince(UUID companyId, Instant since) {
        return jdbc.sql("""
                        SELECT count(*) FROM application_event e
                        JOIN application a ON a.id = e.application_id
                        JOIN job_offer j ON j.id = a.job_offer_id
                        WHERE j.company_id = :company AND e.event_type = 'STATUS_CHANGED'
                          AND e.to_status = 'HIRED' AND e.occurred_at >= :since""")
                .param("company", companyId).param("since", Timestamp.from(since))
                .query(Long.class).single();
    }

    /** Entretiens SCHEDULED qui commencent dans les 7 prochains jours, les plus proches d'abord. */
    public List<UpcomingInterview> upcomingInterviews(UUID companyId, Instant now, int limit) {
        return jdbc.sql("""
                        SELECT i.id, i.application_id, i.starts_at, i.type, u.first_name, u.last_name, j.title
                        FROM interview i
                        JOIN application a ON a.id = i.application_id
                        JOIN app_user u ON u.id = a.candidate_id
                        JOIN job_offer j ON j.id = i.job_offer_id
                        WHERE i.company_id = :company AND i.status = 'SCHEDULED'
                          AND i.starts_at >= :now AND i.starts_at < :end
                        ORDER BY i.starts_at, i.id LIMIT :limit""")
                .param("company", companyId).param("now", Timestamp.from(now))
                .param("end", Timestamp.from(now.plus(WEEK))).param("limit", limit)
                .query((rs, n) -> new UpcomingInterview(rs.getObject("id", UUID.class),
                        rs.getObject("application_id", UUID.class), rs.getTimestamp("starts_at").toInstant(),
                        rs.getString("type"), (rs.getString("first_name") + " " + rs.getString("last_name")).strip(),
                        rs.getString("title")))
                .list();
    }

    /** Candidatures par statut actuel pour chaque offre non archivée, offres récentes d'abord. */
    public List<JobRow> perJob(UUID companyId, int limit) {
        record Flat(UUID id, String title, String status, String applicationStatus, long total) {
        }
        List<Flat> flat = jdbc.sql("""
                        WITH jobs AS (
                            SELECT id, title, status, updated_at FROM job_offer
                            WHERE company_id = :company AND status <> 'ARCHIVED'
                            ORDER BY updated_at DESC, id DESC LIMIT :limit)
                        SELECT j.id, j.title, j.status, a.status AS application_status, count(a.id) AS total
                        FROM jobs j LEFT JOIN application a ON a.job_offer_id = j.id
                        GROUP BY j.id, j.title, j.status, j.updated_at, a.status
                        ORDER BY j.updated_at DESC, j.id DESC""")
                .param("company", companyId).param("limit", limit)
                .query((rs, n) -> new Flat(rs.getObject("id", UUID.class), rs.getString("title"),
                        rs.getString("status"), rs.getString("application_status"), rs.getLong("total")))
                .list();
        Map<UUID, JobRow> rows = new LinkedHashMap<>();
        for (Flat row : flat) {
            JobRow job = rows.computeIfAbsent(row.id(),
                    id -> new JobRow(id, row.title(), row.status(), new LinkedHashMap<>()));
            if (row.applicationStatus() != null) {
                job.countsByStatus().put(row.applicationStatus(), row.total());
            }
        }
        return List.copyOf(rows.values());
    }

    // ---------------------------------------------------------------- candidat (US-45)

    public Map<String, Long> applicationsByInternalStatus(UUID candidateId) {
        Map<String, Long> counts = new LinkedHashMap<>();
        jdbc.sql("SELECT status, count(*) FROM application WHERE candidate_id = :candidate GROUP BY status")
                .param("candidate", candidateId)
                .query((rs, n) -> Map.entry(rs.getString(1), rs.getLong(2))).list()
                .forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    /** Prochains entretiens planifiés du candidat ; ni avis ni participants (RG-23). */
    public List<CandidateInterview> candidateInterviews(UUID candidateId, Instant now, int limit) {
        return jdbc.sql("""
                        SELECT i.id, i.starts_at, i.duration_minutes, i.type, j.title, c.name
                        FROM interview i
                        JOIN application a ON a.id = i.application_id
                        JOIN job_offer j ON j.id = i.job_offer_id
                        JOIN company c ON c.id = i.company_id
                        WHERE a.candidate_id = :candidate AND i.status = 'SCHEDULED' AND i.starts_at >= :now
                        ORDER BY i.starts_at, i.id LIMIT :limit""")
                .param("candidate", candidateId).param("now", Timestamp.from(now)).param("limit", limit)
                .query((rs, n) -> new CandidateInterview(rs.getObject("id", UUID.class),
                        rs.getTimestamp("starts_at").toInstant(), rs.getInt("duration_minutes"), rs.getString("type"),
                        rs.getString("title"), rs.getString("name")))
                .list();
    }

    public record ApplicationCounts(long last7Days, long last30Days, long inScreening, long shortlisted) {
    }

    public record UpcomingInterview(UUID id, UUID applicationId, Instant startsAt, String type, String candidateName,
                                    String jobTitle) {
    }

    public record JobRow(UUID jobId, String title, String status, Map<String, Long> countsByStatus) {
    }

    public record CandidateInterview(UUID id, Instant startsAt, int durationMinutes, String type, String jobTitle,
                                     String companyName) {
    }

    // --- Tableau de bord admin (US-46) : toute la plateforme, aucune donnée personnelle ---

    /** Comptes par rôle, actifs et désactivés. */
    /** Tous les rôles sont présents, à zéro s'il le faut : le frontend n'a pas à deviner. */
    public Map<String, AccountCounts> accountsByRole() {
        Map<String, AccountCounts> counts = new TreeMap<>();
        for (Role role : Role.values()) {
            counts.put(role.name(), new AccountCounts(0, 0));
        }
        jdbc.sql("""
                        select role, count(*) filter (where enabled) as active,
                               count(*) filter (where not enabled) as disabled
                        from app_user group by role""")
                .query((rs, row) -> Map.entry(rs.getString("role"),
                        new AccountCounts(rs.getLong("active"), rs.getLong("disabled"))))
                .list().forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    public AccountCounts companies() {
        return jdbc.sql("""
                        select count(*) filter (where active) as active, count(*) filter (where not active) as disabled
                        from company""")
                .query((rs, row) -> new AccountCounts(rs.getLong("active"), rs.getLong("disabled"))).single();
    }

    public Map<String, Long> allJobsByStatus() {
        Map<String, Long> counts = new TreeMap<>(Map.of("DRAFT", 0L, "PUBLISHED", 0L, "CLOSED", 0L, "ARCHIVED", 0L));
        jdbc.sql("select status, count(*) from job_offer group by status")
                .query((rs, row) -> Map.entry(rs.getString(1), rs.getLong(2)))
                .list().forEach(entry -> counts.put(entry.getKey(), entry.getValue()));
        return counts;
    }

    public PlatformApplications applications(Instant since) {
        return jdbc.sql("select count(*), count(*) filter (where submitted_at >= :since) from application")
                .param("since", Timestamp.from(since))
                .query((rs, row) -> new PlatformApplications(rs.getLong(1), rs.getLong(2))).single();
    }

    /** @param disabled comptes désactivés, ou entreprises inactives */
    public record AccountCounts(long active, long disabled) {
    }

    public record PlatformApplications(long total, long last30Days) {
    }
}
