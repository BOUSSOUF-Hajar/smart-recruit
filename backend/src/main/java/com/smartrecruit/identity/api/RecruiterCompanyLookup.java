package com.smartrecruit.identity.api;

import java.util.Optional;
import java.util.UUID;

/**
 * Entreprise d'un recruteur, à placer dans son access token ({@code companyId}).
 * <p>
 * Implémentée par le module Entreprises (phase 5). L'inversion évite que le module Identité
 * dépende de lui : c'est Entreprises qui dépend d'Identité, jamais l'inverse.
 */
public interface RecruiterCompanyLookup {

    Optional<UUID> companyOf(UUID recruiterId);
}
