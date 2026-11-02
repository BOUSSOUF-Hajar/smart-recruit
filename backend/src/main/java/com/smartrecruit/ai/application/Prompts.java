package com.smartrecruit.ai.application;

/**
 * Versions des prompts, enregistrées avec chaque résultat (RG-27). Une modification de prompt crée un
 * nouveau fichier et une nouvelle version : un ancien résultat reste explicable.
 */
public final class Prompts {

    public static final String RESUME_EXTRACTION = "resume-extraction-v1";
    public static final String MATCH_EXPLANATION = "match-explain-v1";

    private Prompts() {
    }
}
