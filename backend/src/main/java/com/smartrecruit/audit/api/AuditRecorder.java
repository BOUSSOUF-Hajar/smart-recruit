package com.smartrecruit.audit.api;

import java.util.UUID;

/**
 * Écriture dans le journal d'audit (F-AUDIT-01, RG-24).
 * <p>
 * Deux modes, selon ce que doit devenir l'entrée si l'action échoue :
 * <ul>
 *   <li>{@link #record} rejoint la transaction de l'action : l'action et sa trace réussissent ou
 *       échouent ensemble. Exige une transaction en cours.</li>
 *   <li>{@link #recordIndependently} écrit dans sa propre transaction : la trace survit à l'annulation
 *       de l'action. Réservé aux échecs qu'il faut garder (connexion refusée, accès interdit).</li>
 * </ul>
 */
public interface AuditRecorder {

    /** @return l'identifiant de l'entrée créée */
    UUID record(AuditEvent event);

    /** @return l'identifiant de l'entrée créée */
    UUID recordIndependently(AuditEvent event);
}
