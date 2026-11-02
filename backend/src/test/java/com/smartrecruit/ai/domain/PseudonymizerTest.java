package com.smartrecruit.ai.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * US-43 : sur le jeu de CV fictifs du banc d'essai, aucune donnée personnelle listée dans
 * {@code expected.json} ne survit à la pseudonymisation, et les compétences restent lisibles.
 */
class PseudonymizerTest {

    private static final Path DATASET = Path.of("scripts/ai-benchmark");
    private static final JsonNode EXPECTED = readExpected();

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"cv-01", "cv-02", "cv-03", "cv-04", "cv-05", "cv-06", "cv-07", "cv-08", "cv-09", "cv-10"})
    void personalDataNeverReachesTheModel(String cv) throws IOException {
        String text = Files.readString(DATASET.resolve("cvs/" + cv + ".txt"));
        String name = text.lines().findFirst().orElseThrow().strip();   // la candidature connaît le nom

        String sent = Pseudonymizer.pseudonymize(text, List.of(name));

        List<String> personal = new ArrayList<>();
        EXPECTED.path(cv).path("personalData").forEach(value -> personal.add(value.asString()));
        assertThat(personal).isNotEmpty();
        for (String value : personal) {
            assertThat(sent).as("%s ne doit pas contenir « %s »", cv, value).doesNotContainIgnoringCase(value);
        }
        // Les compétences attendues restent dans le texte : seule l'identité disparaît
        EXPECTED.path(cv).path("skills").forEach(skill -> {
            if (text.contains(skill.asString())) {
                assertThat(sent).contains(skill.asString());
            }
        });
    }

    @Test
    void yearRangesAndDurationsAreNotMistakenForPhoneNumbers() {
        String sent = Pseudonymizer.pseudonymize("2019 - 2021 Développeuse, 5 ans d'expérience, 2 000 utilisateurs",
                List.of());

        assertThat(sent).isEqualTo("2019 - 2021 Développeuse, 5 ans d'expérience, 2 000 utilisateurs");
    }

    @Test
    void markersAreNeutral() {
        String sent = Pseudonymizer.pseudonymize("""
                Salma Idrissi - salma@exemple.ma - 06 12 34 56 78
                Née le 3 mars 1996
                Nationalité : marocaine""", List.of("Salma Idrissi"));

        assertThat(sent).isEqualTo("""
                [CANDIDAT] [CANDIDAT] - [EMAIL] - [TÉLÉPHONE]
                [DATE_DE_NAISSANCE]
                [INFORMATION_PERSONNELLE]""");
    }

    private static JsonNode readExpected() {
        try {
            return JsonMapper.builder().build().readTree(Files.readString(DATASET.resolve("expected.json")));
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
