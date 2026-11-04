package com.smartrecruit.ai.infrastructure.ollama;

import com.smartrecruit.ai.application.AIService;
import com.smartrecruit.ai.application.AiException;
import com.smartrecruit.ai.application.Prompts;
import com.smartrecruit.ai.domain.MatchExplanation;
import com.smartrecruit.ai.domain.ResumeExtraction;
import com.smartrecruit.ai.infrastructure.PromptLibrary;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adaptateur Ollama (architecture §6) : {@code POST /api/chat}, sans flux, schéma JSON imposé dans
 * {@code format}, température 0, raisonnement coupé si {@code think=false} (banc d'essai : sinon trop lent
 * sur CPU). Le texte du CV est passé comme donnée délimitée, jamais comme consigne ; le modèle n'a accès
 * à aucun outil.
 * <p>
 * Réponse désérialisée en record puis validée ; non conforme → une nouvelle tentative, puis
 * {@code AI_INVALID_RESPONSE}. Injoignable ou délai dépassé → {@code AI_UNAVAILABLE}. Les champs en
 * trop (un « score » proposé par le modèle, par exemple) sont ignorés.
 */
public class OllamaAIService implements AIService {

    private static final Logger log = LoggerFactory.getLogger(OllamaAIService.class);
    static final int MAX_ATTEMPTS = 2;

    private final RestClient client;
    private final String model;
    private final Boolean think;
    private final PromptLibrary prompts;
    private final JsonMapper json;
    private final Validator validator;

    /** @param think {@code null} : champ absent de la requête, le modèle décide */
    public OllamaAIService(RestClient client, String model, Boolean think, PromptLibrary prompts, JsonMapper json,
                           Validator validator) {
        this.client = client;
        this.model = model;
        this.think = think;
        this.prompts = prompts;
        // Champs inconnus ignorés : seul ce que le schéma décrit est lu
        this.json = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.validator = validator;
    }

    @Override
    public ResumeExtraction extractResume(String pseudonymizedResume) {
        return chat(Prompts.RESUME_EXTRACTION, "<cv>\n" + pseudonymizedResume + "\n</cv>", ResumeExtraction.class);
    }

    @Override
    public MatchExplanation explainMatch(String explanationInput) {
        return chat(Prompts.MATCH_EXPLANATION, "<donnees>\n" + explanationInput + "\n</donnees>",
                MatchExplanation.class);
    }

    @Override
    public String modelName() {
        return model;
    }

    private <T> T chat(String promptVersion, String userContent, Class<T> type) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", false);
        body.put("format", prompts.schema(promptVersion));
        body.put("options", Map.of("temperature", 0));
        if (think != null) {
            body.put("think", think);
        }
        body.put("messages", List.of(
                Map.of("role", "system", "content", prompts.instructions(promptVersion)),
                Map.of("role", "user", "content", userContent)));
        String lastProblem = "";
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            String content = call(body);
            try {
                T parsed = json.readValue(content, type);
                Set<ConstraintViolation<T>> violations = validator.validate(parsed);
                if (violations.isEmpty()) {
                    return parsed;
                }
                lastProblem = violations.iterator().next().getPropertyPath() + " "
                        + violations.iterator().next().getMessage();
            } catch (JacksonException e) {
                lastProblem = "JSON illisible";
            }
            // Jamais le contenu de la réponse dans les logs : il dérive d'un CV
            log.warn("Réponse IA non conforme ({}), tentative {}/{} : {}", promptVersion, attempt, MAX_ATTEMPTS,
                    lastProblem);
        }
        throw AiException.invalidResponse("Réponse du modèle non conforme au schéma : " + lastProblem);
    }

    private String call(Map<String, Object> body) {
        try {
            JsonNode response = client.post().uri("/api/chat")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class);
            JsonNode content = response == null ? null : response.path("message").path("content");
            if (content == null || !content.isString()) {
                throw AiException.invalidResponse("Réponse d'Ollama sans contenu");
            }
            return content.asString();
        } catch (ResourceAccessException e) {
            throw AiException.unavailable("Service IA injoignable ou trop lent.");
        } catch (RestClientResponseException e) {
            throw AiException.unavailable("Service IA en erreur (HTTP " + e.getStatusCode().value() + ").");
        } catch (RestClientException e) {
            throw AiException.unavailable("Service IA indisponible.");
        }
    }
}
