package com.smartrecruit.shared.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.HexFormat;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

/**
 * Donne à chaque requête un identifiant de corrélation : repris de l'en-tête {@code X-Correlation-Id}
 * s'il est fourni et bien formé, généré sinon. Il est placé dans le MDC (donc dans chaque ligne de log
 * de la requête) et renvoyé dans la réponse.
 * <p>
 * Premier filtre de la chaîne : même une requête refusée par la sécurité aura son identifiant.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    /** Valeur acceptée depuis le client : courte et sans caractère spécial (pas d'injection dans les logs). */
    private static final Pattern VALID = Pattern.compile("[A-Za-z0-9-]{8,64}");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String correlationId = resolve(request.getHeader(HEADER));
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(MDC_KEY);
        }
    }

    static String resolve(String candidate) {
        if (candidate != null && VALID.matcher(candidate).matches()) {
            return candidate;
        }
        byte[] bytes = new byte[8];
        ThreadLocalRandom.current().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);   // 16 caractères hexadécimaux
    }

    /** Identifiant de la requête en cours, ou {@code null} hors requête HTTP (tâche de fond). */
    public static String current() {
        return MDC.get(MDC_KEY);
    }
}
