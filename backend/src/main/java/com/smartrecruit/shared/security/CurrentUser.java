package com.smartrecruit.shared.security;

import java.util.Objects;
import java.util.UUID;

/**
 * L'utilisateur de la requête, reconstruit à partir de l'access token à chaque appel.
 * <p>
 * Les services le reçoivent en paramètre pour leurs contrôles de propriété (RG-05, RG-06) :
 * l'entreprise d'un recruteur vient toujours du token, jamais d'un paramètre envoyé par le client.
 *
 * @param companyId entreprise du recruteur ; {@code null} pour les autres rôles
 */
public record CurrentUser(UUID id, Role role, UUID companyId) {

    public CurrentUser {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(role, "role");
    }

    public boolean isRecruiterOf(UUID company) {
        return role == Role.RECRUITER && companyId != null && companyId.equals(company);
    }
}
