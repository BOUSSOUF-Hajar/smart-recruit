package com.smartrecruit.job.infrastructure;

import com.smartrecruit.shared.web.PageResponse;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Recherche publique en SQL natif (US-25) : la recherche plein texte de PostgreSQL ({@code @@},
 * {@code plainto_tsquery}) n'a pas d'équivalent JPQL. La requête est assemblée à partir de
 * fragments fixes ; les valeurs saisies ne passent que par des paramètres nommés.
 * <p>
 * Ne lit que les tables du module (offres et leurs compétences) : les noms d'entreprises et de
 * compétences sont ajoutés ensuite par les API des modules concernés.
 */
@Repository
public class JobSearchRepository {

    private static final String COLUMNS = """
            j.id, j.company_id, j.title, j.city, j.country_code, j.work_mode, j.contract_type,
            j.experience_level, j.salary_min, j.salary_max, j.salary_currency, j.salary_period,
            j.salary_visible, j.published_at, j.expires_at""";

    private final JdbcClient jdbc;

    public JobSearchRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public PageResponse<JobSearchRow> search(JobSearchCriteria criteria, Instant now) {
        Map<String, Object> params = new HashMap<>();
        String where = where(criteria, now, params);

        long total = jdbc.sql("SELECT count(*) FROM job_offer j WHERE " + where)
                .params(params).query(Long.class).single();
        if (total == 0) {
            return PageResponse.of(List.of(), criteria.page(), criteria.size(), 0);
        }
        params.put("limit", criteria.size());
        params.put("offset", (long) criteria.page() * criteria.size());
        List<JobSearchRow> rows = jdbc.sql("SELECT " + COLUMNS + " FROM job_offer j WHERE " + where
                        + " ORDER BY " + criteria.sort().orderBy() + ", j.id DESC LIMIT :limit OFFSET :offset")
                .params(params)
                .query(JobSearchRepository::row)
                .list();
        return PageResponse.of(rows, criteria.page(), criteria.size(), total);
    }

    /** Compétences requises des offres affichées, dans l'ordre d'enregistrement, au plus {@code perJob} chacune. */
    public Map<UUID, List<UUID>> requiredSkills(Collection<UUID> jobIds, int perJob) {
        if (jobIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<UUID>> result = new LinkedHashMap<>();
        jdbc.sql("""
                        SELECT job_offer_id, skill_id FROM job_offer_skill
                        WHERE job_offer_id IN (:ids) AND requirement = 'REQUIRED'
                        ORDER BY job_offer_id, skill_id""")
                .param("ids", jobIds)
                .query((rs, rowNum) -> Map.entry(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)))
                .list()
                .forEach(entry -> {
                    List<UUID> skills = result.computeIfAbsent(entry.getKey(), id -> new java.util.ArrayList<>());
                    if (skills.size() < perJob) {
                        skills.add(entry.getValue());
                    }
                });
        return result;
    }

    private static String where(JobSearchCriteria criteria, Instant now, Map<String, Object> params) {
        StringBuilder where = new StringBuilder("j.status = 'PUBLISHED' AND j.expires_at > :now");
        params.put("now", Timestamp.from(now));
        if (criteria.query() != null && !criteria.query().isBlank()) {
            // Même chaîne de traitement que la colonne générée : sans accents, racines françaises
            where.append(" AND j.search_vector @@ plainto_tsquery('french', immutable_unaccent(:query))");
            params.put("query", criteria.query());
        }
        if (criteria.city() != null && !criteria.city().isBlank()) {
            where.append(" AND lower(j.city) = lower(:city)");
            params.put("city", criteria.city().strip());
        }
        if (criteria.countryCode() != null && !criteria.countryCode().isBlank()) {
            where.append(" AND j.country_code = upper(:countryCode)");
            params.put("countryCode", criteria.countryCode().strip());
        }
        if (!criteria.workModes().isEmpty()) {
            where.append(" AND j.work_mode IN (:workModes)");
            params.put("workModes", criteria.workModes().stream().map(Enum::name).toList());
        }
        if (!criteria.contractTypes().isEmpty()) {
            where.append(" AND j.contract_type IN (:contractTypes)");
            params.put("contractTypes", criteria.contractTypes().stream().map(Enum::name).toList());
        }
        if (!criteria.experienceLevels().isEmpty()) {
            where.append(" AND j.experience_level IN (:experienceLevels)");
            params.put("experienceLevels", criteria.experienceLevels().stream().map(Enum::name).toList());
        }
        if (criteria.categoryId() != null) {
            where.append(" AND j.category_id = :categoryId");
            params.put("categoryId", criteria.categoryId());
        }
        if (!criteria.skillIds().isEmpty()) {
            // Toutes les compétences demandées, requises ou souhaitées
            where.append(" AND (SELECT count(*) FROM job_offer_skill s WHERE s.job_offer_id = j.id"
                    + " AND s.skill_id IN (:skillIds)) = :skillCount");
            params.put("skillIds", criteria.skillIds());
            params.put("skillCount", criteria.skillIds().size());
        }
        if (criteria.salaryMin() != null) {
            // Seulement les salaires rendus publics : un salaire masqué ne doit pas se deviner par filtrage
            where.append(" AND j.salary_visible AND j.salary_max >= :salaryMin");
            params.put("salaryMin", criteria.salaryMin());
        }
        return where.toString();
    }

    private static JobSearchRow row(ResultSet rs, int rowNum) throws SQLException {
        return new JobSearchRow(
                rs.getObject("id", UUID.class),
                rs.getObject("company_id", UUID.class),
                rs.getString("title"),
                rs.getString("city"),
                rs.getString("country_code"),
                rs.getString("work_mode"),
                rs.getString("contract_type"),
                rs.getString("experience_level"),
                rs.getBigDecimal("salary_min"),
                rs.getBigDecimal("salary_max"),
                rs.getString("salary_currency"),
                rs.getString("salary_period"),
                rs.getBoolean("salary_visible"),
                instant(rs.getTimestamp("published_at")),
                instant(rs.getTimestamp("expires_at")));
    }

    private static Instant instant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
