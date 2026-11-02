package com.smartrecruit.identity.api;

import com.smartrecruit.shared.security.Role;

import java.util.UUID;

/**
 * Publié à la création d'un compte recruteur ou admin, dans la transaction de création.
 * <p>
 * Le module Entreprises y crée le profil recruteur, après avoir vérifié que l'entreprise est active :
 * s'il refuse, la création du compte est annulée. Identité ignore tout des entreprises.
 *
 * @param companyId entreprise du recruteur ; {@code null} pour un admin
 */
public record StaffAccountCreated(UUID userId, Role role, UUID companyId, String jobTitle) {
}
