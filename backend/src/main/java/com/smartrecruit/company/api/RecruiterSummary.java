package com.smartrecruit.company.api;

import java.util.UUID;

/** Recruteur d'une entreprise, tel que les autres modules peuvent l'afficher. */
public record RecruiterSummary(UUID userId, String firstName, String lastName, String jobTitle) {

    public String fullName() {
        return (firstName + " " + lastName).strip();
    }
}
