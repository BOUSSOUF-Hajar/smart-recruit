package com.smartrecruit.candidate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;

/**
 * Lien professionnel. La colonne {@code id} de la table est remplie par PostgreSQL ({@code uuidv7()}) :
 * un lien n'a pas d'identité propre, la liste est toujours remplacée en bloc.
 */
@Embeddable
public class CandidateLink {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LinkType type;

    @Column(nullable = false, length = 255)
    private String url;

    protected CandidateLink() {
        // JPA
    }

    public CandidateLink(LinkType type, String url) {
        this.type = Objects.requireNonNull(type, "type");
        this.url = Objects.requireNonNull(url, "url").strip();
        if (!this.url.startsWith("https://")) {
            throw new IllegalArgumentException("Lien non https");   // garanti en amont par @HttpsUrl
        }
    }

    public LinkType getType() {
        return type;
    }

    public String getUrl() {
        return url;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CandidateLink that && type == that.type && url.equals(that.url);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, url);
    }
}
