package com.smartrecruit.candidate.domain;

/** Limites du profil (US-15, US-16, US-17) ; reprises par les DTO, le frontend et les contraintes SQL. */
public final class ProfileRules {

    public static final int MAX_SKILLS = 50;
    public static final int MAX_LANGUAGES = 20;
    public static final int MAX_LINKS = 5;

    private ProfileRules() {
    }
}
