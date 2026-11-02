package com.smartrecruit.identity.web;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Pas de règle de complexité ici : on vérifie un mot de passe existant, pas un nouveau. */
public record LoginRequest(
        @NotBlank @Size(max = 254) String email,
        @NotBlank @Size(max = 128) String password) {
}
