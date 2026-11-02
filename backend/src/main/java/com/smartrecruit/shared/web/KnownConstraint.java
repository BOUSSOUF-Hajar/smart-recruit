package com.smartrecruit.shared.web;

import com.smartrecruit.shared.domain.ErrorCode;

/**
 * Contrainte de base dont la violation correspond à une erreur métier connue.
 * Chaque module déclare les siennes comme beans ; le {@code GlobalExceptionHandler} traduit
 * alors la violation en code du catalogue au lieu d'un 500.
 * <p>
 * Exemple : deux inscriptions simultanées avec le même email passent toutes deux la vérification
 * applicative ; seul l'index unique {@code uk_app_user_email} arrête la seconde.
 *
 * @param name    nom de la contrainte ou de l'index, tel qu'écrit dans la migration
 * @param message message destiné à l'utilisateur
 */
public record KnownConstraint(String name, ErrorCode code, String message) {
}
