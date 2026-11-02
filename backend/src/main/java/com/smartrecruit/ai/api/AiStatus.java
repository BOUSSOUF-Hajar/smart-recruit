package com.smartrecruit.ai.api;

/** État du service d'IA, tel qu'un admin doit le voir (US-46). */
public interface AiStatus {

    Snapshot current();

    /** @param model modèle configuré ; {@code null} si l'IA est désactivée */
    record Snapshot(State state, String model) {
    }

    enum State {
        /** Désactivée par configuration : aucune fonction IA proposée. */
        DISABLED,
        AVAILABLE,
        /** Activée mais le serveur de modèles ne répond pas, ou le modèle n'est pas téléchargé. */
        UNAVAILABLE
    }
}
