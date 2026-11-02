package com.smartrecruit.ai.application;

import com.smartrecruit.ai.domain.MatchExplanation;
import com.smartrecruit.ai.domain.ResumeExtraction;

/**
 * Fournisseur d'IA (D7.1, US-40). Le métier ne connaît que cette interface : Ollama, un autre
 * fournisseur ou une fausse implémentation de test s'échangent sans toucher au reste.
 * <p>
 * Contrat : le texte reçu est déjà pseudonymisé ; la réponse est validée (schéma et contraintes)
 * avant d'être renvoyée. Erreurs : {@link AiException} avec {@code AI_UNAVAILABLE} (injoignable,
 * délai dépassé) ou {@code AI_INVALID_RESPONSE} (non conforme après une nouvelle tentative).
 */
public interface AIService {

    /** Prompt {@link Prompts#RESUME_EXTRACTION}. */
    ResumeExtraction extractResume(String pseudonymizedResume);

    /** Prompt {@link Prompts#MATCH_EXPLANATION} : explique un score déjà calculé, sans pouvoir le changer. */
    MatchExplanation explainMatch(String explanationInput);

    /** Nom exact du modèle, enregistré avec chaque résultat (RG-27). */
    String modelName();
}
