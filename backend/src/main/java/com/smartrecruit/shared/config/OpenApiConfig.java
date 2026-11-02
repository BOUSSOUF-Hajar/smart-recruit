package com.smartrecruit.shared.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * En-tête de la description OpenAPI générée par springdoc (US-58).
 * <p>
 * Les chemins et les schémas viennent des contrôleurs ; le contrat complet (règles, codes d'erreur,
 * matrice d'accès) reste dans {@code docs/api.md}. Le schéma « bearer » permet d'essayer les endpoints
 * protégés depuis Swagger UI avec un access token obtenu par {@code POST /api/v1/auth/login}.
 */
@Configuration(proxyBeanMethods = false)
public class OpenApiConfig {

    static final String BEARER = "bearerAuth";

    @Bean
    public OpenAPI smartRecruitOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("SmartRecruit API")
                        .version("v1")
                        .description("API REST de SmartRecruit, ATS multi-entreprises. Contrat détaillé, "
                                + "règles métier et catalogue des erreurs : docs/api.md du dépôt."))
                .components(new Components().addSecuritySchemes(BEARER, new SecurityScheme()
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                        .description("Access token (15 minutes) renvoyé par POST /api/v1/auth/login")))
                .addSecurityItem(new SecurityRequirement().addList(BEARER));
    }
}
