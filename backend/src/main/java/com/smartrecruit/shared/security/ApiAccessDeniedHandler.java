package com.smartrecruit.shared.security;

import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.web.ProblemResponseWriter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;

import java.io.IOException;

/** Réponse 403 {@code ACCESS_DENIED} : utilisateur connecté, mais rôle insuffisant (US-10). */
public class ApiAccessDeniedHandler implements AccessDeniedHandler {

    private final ProblemResponseWriter writer;

    public ApiAccessDeniedHandler(ProblemResponseWriter writer) {
        this.writer = writer;
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException exception) throws IOException {
        writer.write(request, response, ErrorCode.ACCESS_DENIED, "Votre rôle ne permet pas cette action.");
    }
}
