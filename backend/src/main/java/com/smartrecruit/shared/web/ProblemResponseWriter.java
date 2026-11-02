package com.smartrecruit.shared.web;

import com.smartrecruit.shared.domain.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * Écrit une {@link ApiProblem} directement dans la réponse, pour les erreurs levées hors des
 * contrôleurs (filtres de sécurité), que le {@code GlobalExceptionHandler} ne voit pas.
 */
@Component
public class ProblemResponseWriter {

    private final JsonMapper jsonMapper;

    public ProblemResponseWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    public void write(HttpServletRequest request, HttpServletResponse response, ErrorCode code, String detail)
            throws IOException {
        int status = ErrorCatalog.status(code).value();
        ApiProblem problem = new ApiProblem(code, status, detail, request.getRequestURI(),
                CorrelationIdFilter.current(), List.of(), Map.of());
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        jsonMapper.writeValue(response.getOutputStream(), problem);
    }
}
