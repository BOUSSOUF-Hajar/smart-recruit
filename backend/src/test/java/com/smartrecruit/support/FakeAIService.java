package com.smartrecruit.support;

import com.smartrecruit.ai.application.AIService;
import com.smartrecruit.ai.application.AiException;
import com.smartrecruit.ai.domain.MatchExplanation;
import com.smartrecruit.ai.domain.ResumeExtraction;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Fausse IA des tests (US-40) : réponses fixes et déterministes, aucun modèle. Elle repère des noms de
 * compétences connus dans le texte reçu (avec la ligne qui les contient comme extrait), garde le
 * dernier texte reçu pour vérifier la pseudonymisation, et peut simuler une panne.
 * <p>
 * Chargée par le scan des composants (sources de test) quand l'IA est activée avec {@code provider=fake}.
 */
@Component
@ConditionalOnExpression("${smartrecruit.ai.enabled:false} and '${smartrecruit.ai.provider:}' == 'fake'")
public class FakeAIService implements AIService {

    public static final String MODEL = "fake-model:1";
    private static final List<String> KNOWN_SKILLS = List.of("Java", "Spring Boot", "PostgreSQL", "REST API", "Git",
            "Docker", "Kubernetes", "Kafka", "Angular", "Python");
    private static final Pattern YEARS = Pattern.compile("(\\d{4})\\s*[-–]\\s*(\\d{4})");

    private final AtomicReference<String> lastResumeInput = new AtomicReference<>();
    private final AtomicReference<String> lastExplanationInput = new AtomicReference<>();
    private final AtomicReference<AiException> nextFailure = new AtomicReference<>();

    @Override
    public ResumeExtraction extractResume(String text) {
        lastResumeInput.set(text);
        failIfAsked();
        List<ResumeExtraction.Skill> skills = new ArrayList<>();
        List<String> lines = text.lines().toList();
        for (String skill : KNOWN_SKILLS) {
            lines.stream()
                    .filter(line -> line.toLowerCase(Locale.ROOT).contains(skill.toLowerCase(Locale.ROOT)))
                    .findFirst()
                    .ifPresent(line -> skills.add(new ResumeExtraction.Skill(skill,
                            line.strip().substring(0, Math.min(300, line.strip().length())))));
        }
        List<ResumeExtraction.Experience> experiences = new ArrayList<>();
        Matcher years = YEARS.matcher(text);
        while (years.find()) {
            experiences.add(new ResumeExtraction.Experience("Poste", null, Integer.valueOf(years.group(1)),
                    Integer.valueOf(years.group(2)), null));
        }
        return new ResumeExtraction(skills, experiences, List.of(), List.of(),
                "Profil technique orienté développement backend.");
    }

    @Override
    public MatchExplanation explainMatch(String input) {
        lastExplanationInput.set(input);
        failIfAsked();
        return new MatchExplanation(List.of("Compétences requises largement couvertes."),
                List.of("Certaines compétences souhaitées sont à vérifier en entretien."),
                "Synthèse d'aide à la décision rédigée par la fausse IA.");
    }

    @Override
    public String modelName() {
        return MODEL;
    }

    public String lastResumeInput() {
        return lastResumeInput.get();
    }

    public String lastExplanationInput() {
        return lastExplanationInput.get();
    }

    /** Le prochain appel échoue avec cette erreur (Ollama arrêté, réponse hors schéma…). */
    public void failNextCall(AiException failure) {
        nextFailure.set(failure);
    }

    public void reset() {
        lastResumeInput.set(null);
        lastExplanationInput.set(null);
        nextFailure.set(null);
    }

    private void failIfAsked() {
        AiException failure = nextFailure.getAndSet(null);
        if (failure != null) {
            throw failure;
        }
    }
}
