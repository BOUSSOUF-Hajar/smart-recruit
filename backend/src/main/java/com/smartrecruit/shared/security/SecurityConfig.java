package com.smartrecruit.shared.security;

import com.smartrecruit.shared.web.ProblemResponseWriter;
import jakarta.servlet.DispatcherType;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Clock;

/**
 * Configuration Spring Security (US-10) : API sans état, authentifiée par access token JWT.
 * <p>
 * Les règles reprennent la matrice de {@code docs/api.md} §11, dans le même ordre :
 * tout ce qui n'est pas listé est refusé. Le contrôle de propriété (RG-05, RG-06) reste fait
 * dans les services, avec {@link CurrentUser} : c'est la seconde barrière.
 */
@Configuration(proxyBeanMethods = false)
public class SecurityConfig {

    private static final String API = "/api/v1";
    static final String DOCS = "/api/docs";
    /** Même politique que Nginx (frontend/nginx/security-headers.conf) : rien hors de l'origine. */
    static final String DOCS_CSP = "default-src 'self'; script-src 'self'; style-src 'self' 'unsafe-inline'; "
            + "img-src 'self' data:; font-src 'self' data:; connect-src 'self'; object-src 'none'; "
            + "frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    private static SecretKey secretKey(SecurityProperties properties) {
        byte[] secret = properties.jwt().secret().getBytes(StandardCharsets.UTF_8);
        return new SecretKeySpec(secret, "HmacSHA256");
    }

    @Bean
    JwtEncoder jwtEncoder(SecurityProperties properties) {
        return NimbusJwtEncoder.withSecretKey(secretKey(properties)).algorithm(MacAlgorithm.HS256).build();
    }

    /**
     * Décodeur limité à HS256 (aucun autre algorithme accepté, donc pas d'attaque « alg: none »),
     * avec vérification de l'émetteur, du type et de l'expiration selon l'horloge de l'application,
     * et de la version de session (compte désactivé ou recruteur changé d'entreprise, US-48).
     */
    @Bean
    JwtDecoder jwtDecoder(SecurityProperties properties, Clock clock,
                          ObjectProvider<RevokedAccessTokens> revocations) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(secretKey(properties))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        JwtTimestampValidator timestamps = new JwtTimestampValidator();
        timestamps.setClock(clock);
        timestamps.setAllowEmptyExpiryClaim(false);
        decoder.setJwtValidator(JwtValidators.createDefaultWithValidators(
                timestamps, new JwtIssuerValidator(properties.jwt().issuer()),
                new SessionVersionValidator(revocations)));
        return decoder;
    }

    /**
     * BCrypt par défaut, avec le préfixe {@code {bcrypt}} stocké dans le hash : on pourra passer
     * à Argon2 plus tard sans invalider les mots de passe existants.
     */
    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * Documentation OpenAPI (US-58), qui n'existe que si springdoc est activé (profils dev et demo).
     * Chaîne à part, examinée avant celle de l'API : lecture publique, et une CSP qui laisse
     * Swagger UI charger ses propres scripts, styles et images, là où l'API garde {@code default-src 'none'}.
     */
    @Bean
    @Order(1)
    SecurityFilterChain apiDocsSecurity(HttpSecurity http) throws Exception {
        http
                .securityMatcher(DOCS + "/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP)))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.GET, DOCS + "/**").permitAll()
                        .anyRequest().denyAll());
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain apiSecurity(HttpSecurity http, ProblemResponseWriter writer) throws Exception {
        ApiAuthenticationEntryPoint entryPoint = new ApiAuthenticationEntryPoint(writer);
        ApiAccessDeniedHandler deniedHandler = new ApiAccessDeniedHandler(writer);

        http
                // Pas de session ni de formulaire : le jeton voyage dans l'en-tête Authorization,
                // que le navigateur n'envoie jamais de lui-même. Les deux endpoints à cookie sont
                // protégés par SameSite=Strict et un chemin restreint (docs/api.md §2).
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
                .authorizeHttpRequests(auth -> auth
                        // Redirections internes vers /error : la réponse a déjà été décidée en amont.
                        .dispatcherTypeMatchers(DispatcherType.ERROR, DispatcherType.FORWARD).permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/actuator/**").hasRole(Role.ADMIN.name())
                        // 1. Authentification publique
                        .requestMatchers(HttpMethod.POST, API + "/auth/register", API + "/auth/login",
                                API + "/auth/refresh", API + "/auth/logout").permitAll()
                        // 2. Consultation publique des offres
                        .requestMatchers(HttpMethod.GET, API + "/jobs", API + "/jobs/{id}", API + "/categories").permitAll()
                        // 3. avant 4 : postuler est réservé aux candidats
                        .requestMatchers(HttpMethod.POST, API + "/jobs/{id}/applications").hasRole(Role.CANDIDATE.name())
                        // 4. Gestion des offres
                        .requestMatchers(HttpMethod.POST, API + "/jobs", API + "/jobs/**").hasRole(Role.RECRUITER.name())
                        .requestMatchers(HttpMethod.PUT, API + "/jobs", API + "/jobs/**").hasRole(Role.RECRUITER.name())
                        .requestMatchers(HttpMethod.DELETE, API + "/jobs", API + "/jobs/**").hasRole(Role.RECRUITER.name())
                        // 5 à 7. Compte et référentiel de compétences
                        .requestMatchers(API + "/me", API + "/auth/password").authenticated()
                        .requestMatchers(HttpMethod.GET, API + "/skills").authenticated()
                        .requestMatchers(HttpMethod.POST, API + "/skills")
                        .hasAnyRole(Role.CANDIDATE.name(), Role.RECRUITER.name())
                        // 8 à 10. Espaces par rôle
                        .requestMatchers(API + "/candidates/me", API + "/candidates/me/**").hasRole(Role.CANDIDATE.name())
                        .requestMatchers(API + "/company/**", API + "/applications/**",
                                API + "/interviews/**", API + "/ai/**").hasRole(Role.RECRUITER.name())
                        .requestMatchers(API + "/admin/**").hasRole(Role.ADMIN.name())
                        // 11. Tout le reste
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(roleConverter()))
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(deniedHandler))
                .addFilterAfter(new PasswordChangeRequiredFilter(writer), BearerTokenAuthenticationFilter.class);
        return http.build();
    }

    /** Claim {@code role: "RECRUITER"} → autorité {@code ROLE_RECRUITER}, celle qu'attend {@code hasRole}. */
    static JwtAuthenticationConverter roleConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtClaims.ROLE);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }
}
