package com.smartrecruit.ai.infrastructure;

import com.smartrecruit.ai.application.AIService;
import com.smartrecruit.ai.application.AiException;
import com.smartrecruit.ai.domain.MatchExplanation;
import com.smartrecruit.ai.domain.ResumeExtraction;

/**
 * Assistance IA désactivée ({@code smartrecruit.ai.enabled=false}, D7.3) : tout appel répond
 * {@code AI_DISABLED}. Les endpoints vérifient l'activation avant même de créer une tâche.
 */
public class DisabledAIService implements AIService {

    @Override
    public ResumeExtraction extractResume(String pseudonymizedResume) {
        throw AiException.disabled();
    }

    @Override
    public MatchExplanation explainMatch(String explanationInput) {
        throw AiException.disabled();
    }

    @Override
    public String modelName() {
        return "désactivé";
    }
}
