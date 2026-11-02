package com.smartrecruit.audit.domain;

import java.util.Collection;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Garde-fou : refuse d'écrire dans le journal un détail dont la clé évoque une donnée sensible
 * (mot de passe, token, secret, contenu de CV), y compris dans un objet imbriqué.
 * <p>
 * Une erreur de développement doit échouer bruyamment en test, plutôt que de fuiter en production.
 */
public final class AuditDetailsPolicy {

    private static final Pattern FORBIDDEN_KEY = Pattern.compile(
            "(?i).*(password|passwd|pwd|secret|token|authorization|cookie|resume_?(text|content)|cv_?(text|content)).*");

    private AuditDetailsPolicy() {
    }

    public static void check(Map<String, ?> details) {
        details.forEach((key, value) -> {
            if (FORBIDDEN_KEY.matcher(key).matches()) {
                throw new IllegalArgumentException("Donnée sensible interdite dans le journal d'audit : " + key);
            }
            checkValue(value);
        });
    }

    @SuppressWarnings("unchecked")
    private static void checkValue(Object value) {
        if (value instanceof Map<?, ?> nested) {
            check((Map<String, ?>) nested);
        } else if (value instanceof Collection<?> items) {
            items.forEach(AuditDetailsPolicy::checkValue);
        }
    }
}
