package com.smartrecruit.identity.application;

/**
 * Origine d'une requête d'authentification, gardée avec la session (US-12 : liste des sessions).
 *
 * @param ip        adresse IP vue par l'API ; derrière un proxy, activer {@code server.forward-headers-strategy}
 * @param userAgent navigateur, tronqué à 255 caractères au stockage
 */
public record ClientInfo(String ip, String userAgent) {
}
