package com.smartrecruit.candidate.infrastructure;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.web.KnownConstraint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Contraintes du module traduites en erreurs métier (voir {@link KnownConstraint}). */
@Configuration(proxyBeanMethods = false)
class CandidateConstraints {

    /** Deux téléversements simultanés : le second se heurte à l'index unique partiel (RG-08). */
    @Bean
    KnownConstraint singleActiveResumeConstraint() {
        return new KnownConstraint("uk_resume_active_candidate", ErrorCode.CONCURRENT_MODIFICATION,
                "Un autre CV vient d'être téléversé. Rechargez la page.");
    }
}
