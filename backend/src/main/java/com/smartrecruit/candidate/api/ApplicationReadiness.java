package com.smartrecruit.candidate.api;

import java.util.List;
import java.util.UUID;

/**
 * Profil minimal de RG-09 : prénom et nom ({@code name}), titre ({@code headline}), au moins une
 * compétence ({@code skills}) et un CV actif ({@code resume}).
 *
 * @param missing        critères manquants, mêmes codes que la complétude (le frontend mène au bon onglet)
 * @param activeResumeId CV qui serait figé dans la candidature, ou {@code null}
 */
public record ApplicationReadiness(List<String> missing, UUID activeResumeId) {

    public ApplicationReadiness {
        missing = List.copyOf(missing);
    }

    public boolean ready() {
        return missing.isEmpty();
    }

    public static ApplicationReadiness of(List<String> missing, UUID activeResumeId) {
        return new ApplicationReadiness(missing, activeResumeId);
    }
}
