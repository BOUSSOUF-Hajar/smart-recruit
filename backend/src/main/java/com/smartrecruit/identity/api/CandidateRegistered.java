package com.smartrecruit.identity.api;

import java.util.UUID;

/**
 * Publié à l'inscription d'un candidat, dans la transaction de l'inscription.
 * <p>
 * Le module Candidats y crée le profil vide (RG-04) : si cette création échoue, l'inscription
 * est annulée, et aucun compte candidat n'existe sans profil.
 */
public record CandidateRegistered(UUID userId) {
}
