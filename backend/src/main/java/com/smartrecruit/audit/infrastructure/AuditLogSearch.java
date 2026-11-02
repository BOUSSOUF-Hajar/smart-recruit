package com.smartrecruit.audit.infrastructure;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Consultation du journal (US-53) : filtres combinés par ET, les plus récentes d'abord. La requête
 * est construite avec les seuls filtres présents, pour que PostgreSQL choisisse l'index adapté
 * (période, auteur, action ou résultat). Le nom de l'auteur est lu dans {@code app_user} : seule
 * lecture hors du module, en lecture seule, comme la clé étrangère qui les relie déjà.
 */
@Repository
public class AuditLogSearch {

    private final JdbcClient jdbc;
    private final JsonMapper json;

    AuditLogSearch(JdbcClient jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Transactional(readOnly = true)
    public Result search(Criteria criteria, int page, int size) {
        StringBuilder where = new StringBuilder(" where true");
        Map<String, Object> params = new LinkedHashMap<>();
        if (criteria.from() != null) {
            where.append(" and l.occurred_at >= :from");
            params.put("from", Timestamp.from(criteria.from()));
        }
        if (criteria.to() != null) {
            where.append(" and l.occurred_at < :to");
            params.put("to", Timestamp.from(criteria.to()));
        }
        if (criteria.actorId() != null) {
            where.append(" and l.actor_id = :actor");
            params.put("actor", criteria.actorId());
        }
        if (criteria.action() != null && !criteria.action().isBlank()) {
            where.append(" and l.action = :action");
            params.put("action", criteria.action().strip());
        }
        if (criteria.result() != null) {
            where.append(" and l.result = :result");
            params.put("result", criteria.result());
        }
        long total = jdbc.sql("select count(*) from audit_log l" + where).params(params).query(Long.class).single();
        List<Entry> entries = total == 0 ? List.of() : jdbc.sql("""
                        select l.id, l.occurred_at, l.actor_id, coalesce(l.actor_email, u.email) as actor_email,
                               case when u.id is null then null else u.first_name || ' ' || u.last_name end as actor_name,
                               l.action, l.resource_type, l.resource_id, l.result, host(l.ip_address) as ip,
                               l.correlation_id, l.details::text as details
                        from audit_log l left join app_user u on u.id = l.actor_id""" + where
                        + " order by l.occurred_at desc, l.id desc limit :limit offset :offset")
                .params(params).param("limit", size).param("offset", (long) page * size)
                .query((rs, row) -> new Entry(rs.getObject("id", UUID.class),
                        rs.getTimestamp("occurred_at").toInstant(), rs.getObject("actor_id", UUID.class),
                        rs.getString("actor_email"), rs.getString("actor_name"), rs.getString("action"),
                        rs.getString("resource_type"), rs.getObject("resource_id", UUID.class),
                        rs.getString("result"), rs.getString("ip"), rs.getString("correlation_id"),
                        rs.getString("details") == null ? null : json.readTree(rs.getString("details"))))
                .list();
        return new Result(entries, total);
    }

    /** Actions présentes dans le journal, pour le filtre de l'écran (index {@code ix_audit_log_action}). */
    @Transactional(readOnly = true)
    public List<String> actions() {
        return new ArrayList<>(jdbc.sql("select distinct action from audit_log order by action")
                .query(String.class).list());
    }

    /** @param to borne exclue */
    public record Criteria(Instant from, Instant to, UUID actorId, String action, String result) {
    }

    public record Entry(UUID id, Instant occurredAt, UUID actorId, String actorEmail, String actorName, String action,
                        String resourceType, UUID resourceId, String result, String ipAddress, String correlationId,
                        JsonNode details) {
    }

    public record Result(List<Entry> entries, long total) {
    }
}
