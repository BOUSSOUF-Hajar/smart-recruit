package com.smartrecruit.application.infrastructure;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.domain.HistoryEntry;
import com.smartrecruit.application.domain.HistoryEventType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Historique des candidatures (F-HIST-01, RG-24) en JDBC : une table en ajout seul n'a pas besoin
 * d'entité. Ce dépôt n'expose que l'insertion et la lecture ; aucune méthode ne modifie ni ne supprime.
 */
@Repository
public class HistoryRepository {

    private static final TypeReference<Map<String, Object>> DETAILS = new TypeReference<>() {
    };

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;

    public HistoryRepository(JdbcClient jdbc, JsonMapper jsonMapper) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
    }

    /** Rejoint la transaction en cours : l'entrée et l'action qu'elle décrit sont validées ensemble. */
    public void append(UUID applicationId, HistoryEventType type, ApplicationStatus from, ApplicationStatus to,
                       UUID actorId, Map<String, Object> details, Instant occurredAt) {
        jdbc.sql("""
                        INSERT INTO application_event
                            (application_id, event_type, from_status, to_status, actor_id, details, occurred_at)
                        VALUES (:applicationId, :type, :from, :to, :actorId, cast(:details as jsonb), :occurredAt)""")
                .param("applicationId", applicationId)
                .param("type", type.name())
                .param("from", from == null ? null : from.name())
                .param("to", to == null ? null : to.name())
                .param("actorId", actorId)
                .param("details", details == null || details.isEmpty() ? null : jsonMapper.writeValueAsString(details))
                .param("occurredAt", Timestamp.from(occurredAt))
                .update();
    }

    /** Plus récent d'abord ; à instant égal, l'identifiant UUIDv7 (croissant) départage. */
    public List<HistoryEntry> findByApplication(UUID applicationId) {
        return jdbc.sql("""
                        SELECT id, application_id, event_type, from_status, to_status, actor_id, details, occurred_at
                        FROM application_event WHERE application_id = :applicationId
                        ORDER BY occurred_at DESC, id DESC""")
                .param("applicationId", applicationId)
                .query(this::entry)
                .list();
    }

    private HistoryEntry entry(ResultSet rs, int rowNum) throws SQLException {
        String details = rs.getString("details");
        return new HistoryEntry(rs.getObject("id", UUID.class), rs.getObject("application_id", UUID.class),
                HistoryEventType.valueOf(rs.getString("event_type")), status(rs.getString("from_status")),
                status(rs.getString("to_status")), rs.getObject("actor_id", UUID.class),
                details == null ? Map.of() : jsonMapper.readValue(details, DETAILS),
                rs.getTimestamp("occurred_at").toInstant());
    }

    private static ApplicationStatus status(String value) {
        return value == null ? null : ApplicationStatus.valueOf(value);
    }
}
