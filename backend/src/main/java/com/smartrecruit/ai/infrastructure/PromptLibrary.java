package com.smartrecruit.ai.infrastructure;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Prompts et schémas JSON versionnés dans {@code src/main/resources/prompts} : le nom du fichier est
 * la version enregistrée avec chaque résultat (RG-27). Lus une fois, gardés en mémoire.
 */
@Component
public class PromptLibrary {

    private final JsonMapper json;
    private final Map<String, String> texts = new ConcurrentHashMap<>();
    private final Map<String, JsonNode> schemas = new ConcurrentHashMap<>();

    public PromptLibrary(JsonMapper json) {
        this.json = json;
    }

    public String instructions(String version) {
        return texts.computeIfAbsent(version, v -> read("prompts/" + v + ".txt"));
    }

    public JsonNode schema(String version) {
        return schemas.computeIfAbsent(version, v -> json.readTree(read("prompts/" + v + ".schema.json")));
    }

    private static String read(String path) {
        try {
            return new ClassPathResource(path).getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Prompt introuvable : " + path, e);
        }
    }
}
