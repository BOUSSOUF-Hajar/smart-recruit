package com.smartrecruit.shared.security;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import java.io.IOException;

/**
 * Réponse 401 au format de l'API. Distingue un access token expiré ({@code AUTH_TOKEN_EXPIRED},
 * que le frontend renouvelle silencieusement) de tout autre cas ({@code AUTHENTICATION_REQUIRED} :
 * pas de token, signature invalide, token altéré), qui mène à l'écran de connexion.
 */
public class ApiAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ProblemResponseWriter writer;

    public ApiAuthenticationEntryPoint(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException exception) throws IOException {
        if (isExpiredToken(exception)) {
            writer.write(request, response, ErrorCode.AUTH_TOKEN_EXPIRED, "Votre session doit être renouvelée.");
        } else {
            writer.write(request, response, ErrorCode.AUTHENTICATION_REQUIRED, "Vous devez être connecté.");
        }
    }

    /**
     * Spring Security enveloppe l'erreur de validation du JWT ; on remonte la chaîne des causes
     * jusqu'à {@link JwtValidationException}, dont le validateur d'horodatage écrit « Jwt expired at … ».
     */
    static boolean isExpiredToken(Throwable exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof JwtValidationException validation) {
                return validation.getErrors().stream()
                        .map(OAuth2Error::getDescription)
                        .anyMatch(description -> description != null && description.contains("expired"));
            }
        }
        return false;
    }
}
