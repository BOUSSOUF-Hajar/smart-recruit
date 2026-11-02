package com.smartrecruit.referential.infrastructure;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.web.KnownConstraint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Contraintes des référentiels traduites en erreurs métier : elles arrêtent les cas que la
 * vérification applicative laisse passer sous concurrence (deux créations au même instant,
 * une offre créée pendant la suppression de sa catégorie).
 */
@Configuration(proxyBeanMethods = false)
class ReferentialConstraints {

    @Bean
    KnownConstraint uniqueCategoryNameConstraint() {
        return new KnownConstraint("uk_job_category_name", ErrorCode.RESOURCE_IN_USE,
                "Une catégorie porte déjà ce nom.");
    }

    @Bean
    KnownConstraint usedCategoryConstraint() {
        return new KnownConstraint("fk_job_offer_category", ErrorCode.RESOURCE_IN_USE,
                "Cette catégorie est utilisée par des offres : désactivez-la plutôt.");
    }

    @Bean
    KnownConstraint uniqueSkillNameConstraint() {
        return new KnownConstraint("uk_skill_normalized_name", ErrorCode.RESOURCE_IN_USE,
                "Une compétence porte déjà ce nom.");
    }
}
