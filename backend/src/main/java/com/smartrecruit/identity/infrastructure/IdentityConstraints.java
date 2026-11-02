package com.smartrecruit.identity.infrastructure;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.web.KnownConstraint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contraintes de base du module traduites en erreurs métier (voir {@link KnownConstraint}). */
@Configuration(proxyBeanMethods = false)
class IdentityConstraints {

    @Bean
    KnownConstraint uniqueEmailConstraint() {
        return new KnownConstraint("uk_app_user_email", ErrorCode.EMAIL_ALREADY_USED,
                "Un compte existe déjà avec cet email.");
    }
}
