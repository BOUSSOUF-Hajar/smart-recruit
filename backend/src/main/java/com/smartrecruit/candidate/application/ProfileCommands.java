package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.domain.LanguageLevel;
import com.smartrecruit.candidate.domain.LinkType;
import com.smartrecruit.referential.api.SkillLevel;

import java.util.UUID;

/** Données validées des écritures du profil, indépendantes du format HTTP. */
public final class ProfileCommands {

    private ProfileCommands() {
    }

    public record UpdateBasics(String firstName, String lastName, String phone, String city, String countryCode,
                               String headline, String summary, Integer yearsOfExperience, long version) {
    }

    public record SkillEntry(UUID skillId, SkillLevel level, Integer years) {
    }

    public record LanguageEntry(String languageCode, LanguageLevel level) {
    }

    public record LinkEntry(LinkType type, String url) {
    }
}
