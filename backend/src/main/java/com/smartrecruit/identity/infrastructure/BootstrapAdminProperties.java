package com.smartrecruit.identity.infrastructure;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Premier administrateur, lu dans {@code INITIAL_ADMIN_EMAIL} et {@code INITIAL_ADMIN_PASSWORD}.
 * Les deux peuvent être vides : aucun compte n'est alors créé.
 */
@ConfigurationProperties(prefix = "smartrecruit.bootstrap.admin")
public record BootstrapAdminProperties(String email, String password) {

    boolean isConfigured() {
        return email != null && !email.isBlank() && password != null && !password.isBlank();
    }
}
