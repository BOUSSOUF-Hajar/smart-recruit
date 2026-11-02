package com.smartrecruit.identity.web;

import com.smartrecruit.identity.application.RegistrationCommand;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Corps de {@code POST /auth/register}. Aucun champ {@code role} : s'il est envoyé, il est ignoré
 * (Jackson ne le lie à rien), et le compte est toujours CANDIDATE (RG-02).
 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 254) String email,
        @NotNull @Size(min = 12, max = 128) @NotCommonPassword String password,
        @NotBlank @Size(max = 80) String firstName,
        @NotBlank @Size(max = 80) String lastName,
        @AssertTrue(message = "doit être accepté") boolean privacyAccepted) {

    RegistrationCommand toCommand() {
        return new RegistrationCommand(email, password, firstName, lastName);
    }
}
