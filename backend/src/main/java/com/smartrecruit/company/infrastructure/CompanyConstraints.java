package com.smartrecruit.company.infrastructure;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.web.KnownConstraint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
class CompanyConstraints {

    @Bean
    KnownConstraint uniqueCompanyNameConstraint() {
        return new KnownConstraint("uk_company_name", ErrorCode.RESOURCE_IN_USE,
                "Une entreprise porte déjà ce nom.");
    }
}
