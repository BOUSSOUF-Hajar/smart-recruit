package com.smartrecruit.candidate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Locale;
import java.util.Objects;

/** Langue du profil : code ISO 639 (fr, en, ar, zgh…) et niveau. */
@Embeddable
public class CandidateLanguage {

    @Column(name = "language_code", nullable = false, length = 3)
    private String languageCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private LanguageLevel level;

    protected CandidateLanguage() {
        // JPA
    }

    public CandidateLanguage(String languageCode, LanguageLevel level) {
        this.languageCode = Objects.requireNonNull(languageCode, "languageCode").toLowerCase(Locale.ROOT);
        this.level = Objects.requireNonNull(level, "level");
    }

    public String getLanguageCode() {
        return languageCode;
    }

    public LanguageLevel getLevel() {
        return level;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CandidateLanguage that && languageCode.equals(that.languageCode) && level == that.level;
    }

    @Override
    public int hashCode() {
        return Objects.hash(languageCode, level);
    }
}
