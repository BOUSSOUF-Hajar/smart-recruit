package com.smartrecruit.audit.infrastructure;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.audit.domain.AuditDetailsPolicy;
import com.smartrecruit.shared.web.CorrelationIdFilter;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import tools.jackson.databind.json.JsonMapper;

import java.time.Clock;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Écrit le journal en SQL direct ({@link JdbcClient}) plutôt qu'avec une entité JPA : la table est en
 * ajout seul, sans cycle de vie à gérer, et l'insertion reste lisible ligne à ligne.
 * <p>
 * Le {@code JdbcClient} utilise la même connexion que JPA : il participe donc à la transaction en cours.
 */
@Component
class JdbcAuditRecorder implements AuditRecorder {

    private static final String INSERT = """
            insert into audit_log (occurred_at, actor_id, actor_email, action, resource_type, resource_id,
                                   result, ip_address, correlation_id, details)
            values (:occurredAt, :actorId, :actorEmail, :action, :resourceType, :resourceId,
                    :result, cast(:ip as inet), :correlationId, cast(:details as jsonb))
            returning id
            """;

    private final JdbcClient jdbc;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    JdbcAuditRecorder(JdbcClient jdbc, JsonMapper jsonMapper, Clock clock) {
        this.jdbc = jdbc;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID record(AuditEvent event) {
        return insert(event);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public UUID recordIndependently(AuditEvent event) {
        return insert(event);
    }

    private UUID insert(AuditEvent event) {
        AuditDetailsPolicy.check(event.details());
        return jdbc.sql(INSERT)
                .param("occurredAt", OffsetDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))
                .param("actorId", event.actorId())
                .param("actorEmail", event.actorEmail())
                .param("action", event.action())
                .param("resourceType", event.resourceType())
                .param("resourceId", event.resourceId())
                .param("result", event.result().name())
                .param("ip", currentClientIp())
                .param("correlationId", CorrelationIdFilter.current())
                .param("details", event.details().isEmpty() ? null : jsonMapper.writeValueAsString(event.details()))
                .query(UUID.class)
                .single();
    }

    /**
     * Adresse du client de la requête HTTP en cours, ou {@code null} hors requête (tâche de fond).
     * Derrière Nginx (phase 11), {@code server.forward-headers-strategy} fera remonter la vraie adresse.
     */
    private static String currentClientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return attributes.getRequest().getRemoteAddr();
        }
        return null;
    }
}
