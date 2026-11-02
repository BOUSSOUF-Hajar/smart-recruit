package com.smartrecruit.shared.security;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.web.ProblemResponseWriter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

/**
 * US-11 : tant qu'un compte créé par l'admin n'a pas changé son mot de passe temporaire,
 * seuls {@code /me}, le changement de mot de passe et la déconnexion sont accessibles
 * ({@code 403 PASSWORD_CHANGE_REQUIRED} pour tout le reste).
 * <p>
 * Le marqueur est dans l'access token : aucune lecture en base à chaque requête.
 * Volontairement pas un {@code @Component} : Spring Boot l'enregistrerait aussi comme filtre
 * servlet, hors de la chaîne de sécurité. Il est créé par {@link SecurityConfig}.
 */
public class PasswordChangeRequiredFilter extends OncePerRequestFilter {

    static final Set<String> ALLOWED_PATHS = Set.of("/api/v1/me", "/api/v1/auth/password", "/api/v1/auth/logout");

    private final ProblemResponseWriter writer;

    public PasswordChangeRequiredFilter(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (mustChangePassword() && !ALLOWED_PATHS.contains(request.getRequestURI())) {
            writer.write(request, response, ErrorCode.PASSWORD_CHANGE_REQUIRED,
                    "Vous devez d'abord remplacer votre mot de passe temporaire.");
            return;
        }
        chain.doFilter(request, response);
    }

    private static boolean mustChangePassword() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication instanceof JwtAuthenticationToken token
                && Boolean.TRUE.equals(token.getToken().getClaimAsBoolean(JwtClaims.PASSWORD_CHANGE_REQUIRED));
    }
}
