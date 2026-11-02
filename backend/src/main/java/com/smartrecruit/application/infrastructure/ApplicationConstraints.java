package com.smartrecruit.application.infrastructure;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.web.KnownConstraint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class ApplicationConstraints {

    /** RG-10 : deux envois simultanés passent tous deux la vérification ; l'index arrête le second. */
    @Bean
    KnownConstraint uniqueApplicationConstraint() {
        return new KnownConstraint("uk_application_job_candidate", ErrorCode.APPLICATION_ALREADY_EXISTS,
                "Vous avez déjà postulé à cette offre.");
    }

    /** Un CV supprimé au moment même où il était joint à une candidature (ou l'inverse) : on recharge. */
    @Bean
    KnownConstraint applicationResumeConstraint() {
        return new KnownConstraint("fk_application_resume", ErrorCode.CONCURRENT_MODIFICATION,
                "Votre CV vient de changer. Rechargez la page puis réessayez.");
    }
}
