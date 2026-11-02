package com.smartrecruit.application.infrastructure;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.shared.web.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Liste recruteur des candidatures (US-30) : deux requêtes SQL par page (total, puis lignes),
 * quel que soit le nombre de lignes.
 * <p>
 * <b>Exception assumée aux frontières de modules</b> : cette requête de lecture joint des tables
 * d'autres modules (offres pour l'entreprise et le titre, comptes pour le nom, profils pour le titre
 * professionnel et les compétences). Filtrer par nom ou par compétence en passant par leurs API
 * obligerait à échanger des listes d'identifiants potentiellement longues, ou à paginer en mémoire.
 * Elle reste en lecture seule et isolée ici ; toutes les écritures passent par les API des modules.
 * Si les modules devenaient des services, elle serait remplacée par une projection alimentée par
 * leurs événements.
 */
@Repository
public class RecruiterApplicationSearch {

    private static final String FROM = """
             FROM application a
             JOIN job_offer j ON j.id = a.job_offer_id
             JOIN app_user u ON u.id = a.candidate_id
             JOIN candidate_profile p ON p.user_id = a.candidate_id
            """;

    private final JdbcClient jdbc;

    public RecruiterApplicationSearch(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<RecruiterApplicationRow> search(RecruiterApplicationCriteria criteria) {
        Map<String, Object> params = new HashMap<>();
        String where = where(criteria, params);
        long total = jdbc.sql("SELECT count(*)" + FROM + "WHERE " + where).params(params).query(Long.class).single();
        if (total == 0) {
            return PageResponse.of(List.of(), criteria.page(), criteria.size(), 0);
        }
        params.put("limit", criteria.size());
        params.put("offset", (long) criteria.page() * criteria.size());
        List<RecruiterApplicationRow> rows = jdbc.sql("""
                        SELECT a.id, a.candidate_id, u.first_name, u.last_name, p.headline, a.job_offer_id,
                               j.title, a.status, a.submitted_at, a.last_match_score"""
                        + FROM + "WHERE " + where
                        + " ORDER BY " + criteria.sort().orderBy() + ", a.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query(RecruiterApplicationSearch::row)
                .list();
        return PageResponse.of(rows, criteria.page(), criteria.size(), total);
    }

    private static String where(RecruiterApplicationCriteria criteria, Map<String, Object> params) {
        // RG-05 : toujours limité à l'entreprise du token
        StringBuilder where = new StringBuilder("j.company_id = :companyId");
        params.put("companyId", criteria.companyId());
        if (criteria.jobOfferId() != null) {
            where.append(" AND a.job_offer_id = :jobOfferId");
            params.put("jobOfferId", criteria.jobOfferId());
        }
        if (!criteria.statuses().isEmpty()) {
            where.append(" AND a.status IN (:statuses)");
            params.put("statuses", criteria.statuses().stream().map(Enum::name).toList());
        }
        if (criteria.submittedFrom() != null) {
            where.append(" AND a.submitted_at >= :submittedFrom");
            params.put("submittedFrom", Timestamp.from(criteria.submittedFrom()));
        }
        if (criteria.submittedBefore() != null) {
            where.append(" AND a.submitted_at < :submittedBefore");
            params.put("submittedBefore", Timestamp.from(criteria.submittedBefore()));
        }
        if (criteria.candidateName() != null && !criteria.candidateName().isBlank()) {
            // « contient », sans casse ni accents, dans « prénom nom » comme dans « nom prénom » ;
            // strpos plutôt que LIKE : les % et _ saisis restent des caractères ordinaires
            // (accents retirés avant lower : en collation C, lower ne connaît que l'ASCII)
            where.append(" AND (strpos(lower(immutable_unaccent(u.first_name || ' ' || u.last_name)),"
                    + " lower(immutable_unaccent(:name))) > 0"
                    + " OR strpos(lower(immutable_unaccent(u.last_name || ' ' || u.first_name)),"
                    + " lower(immutable_unaccent(:name))) > 0)");
            params.put("name", criteria.candidateName().strip());
        }
        if (!criteria.skillIds().isEmpty()) {
            // Le candidat déclare toutes les compétences demandées
            where.append(" AND (SELECT count(*) FROM candidate_skill cs WHERE cs.candidate_id = a.candidate_id"
                    + " AND cs.skill_id IN (:skillIds)) = :skillCount");
            params.put("skillIds", criteria.skillIds());
            params.put("skillCount", criteria.skillIds().size());
        }
        if (criteria.minScore() != null) {
            // Les candidatures jamais analysées (score nul) sont exclues
            where.append(" AND a.last_match_score >= :minScore");
            params.put("minScore", criteria.minScore());
        }
        return where.toString();
    }

    private static RecruiterApplicationRow row(ResultSet rs, int rowNum) throws SQLException {
        Integer score = rs.getObject("last_match_score", Integer.class);
        return new RecruiterApplicationRow(rs.getObject("id", UUID.class), rs.getObject("candidate_id", UUID.class),
                rs.getString("first_name"), rs.getString("last_name"), rs.getString("headline"),
                rs.getObject("job_offer_id", UUID.class), rs.getString("title"),
                ApplicationStatus.valueOf(rs.getString("status")), rs.getTimestamp("submitted_at").toInstant(),
                score);
    }
}
