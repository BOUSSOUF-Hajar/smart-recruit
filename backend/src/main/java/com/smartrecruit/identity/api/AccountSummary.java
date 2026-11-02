package com.smartrecruit.identity.api;

import java.util.UUID;

/** Identité d'un compte, telle que les autres modules peuvent la lire. */
public record AccountSummary(UUID id, String email, String firstName, String lastName) {
}
