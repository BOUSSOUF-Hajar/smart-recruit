package com.smartrecruit.audit.api;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Une action à tracer. Construction fluide et immuable :
 * <pre>{@code
 * AuditEvent.success("RESUME_DOWNLOADED")
 *         .by(recruiterId, recruiterEmail)
 *         .on("RESUME", resumeId)
 *         .with("applicationId", applicationId)
 * }</pre>
 * La date, l'adresse IP et l'identifiant de corrélation sont ajoutés par l'{@link AuditRecorder}.
 *
 * @param details informations techniques complémentaires ; jamais de donnée sensible (vérifié à l'écriture)
 */
public record AuditEvent(
        String action,
        AuditResult result,
        UUID actorId,
        String actorEmail,
        String resourceType,
        UUID resourceId,
        Map<String, Object> details
) {

    public AuditEvent {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(result, "result");
        details = details == null ? Map.of() : Map.copyOf(details);
    }

    public static AuditEvent success(String action) {
        return new AuditEvent(action, AuditResult.SUCCESS, null, null, null, null, Map.of());
    }

    public static AuditEvent failure(String action) {
        return new AuditEvent(action, AuditResult.FAILURE, null, null, null, null, Map.of());
    }

    /** Auteur de l'action ; l'identifiant peut être nul (connexion échouée avec un email inconnu). */
    public AuditEvent by(UUID actorId, String actorEmail) {
        return new AuditEvent(action, result, actorId, actorEmail, resourceType, resourceId, details);
    }

    /** Ressource concernée. */
    public AuditEvent on(String resourceType, UUID resourceId) {
        return new AuditEvent(action, result, actorId, actorEmail, resourceType, resourceId, details);
    }

    public AuditEvent with(String key, Object value) {
        Map<String, Object> copy = new LinkedHashMap<>(details);
        copy.put(key, value);
        return new AuditEvent(action, result, actorId, actorEmail, resourceType, resourceId, copy);
    }
}
