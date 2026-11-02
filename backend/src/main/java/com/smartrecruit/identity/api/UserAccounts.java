package com.smartrecruit.identity.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Accès des autres modules aux comptes : le prénom et le nom vivent sur le compte, mais le
 * candidat les modifie depuis son profil (docs/api.md §4).
 */
public interface UserAccounts {

    /** @throws com.smartrecruit.shared.domain.ResourceNotFoundException si le compte n'existe pas */
    AccountSummary get(UUID userId);

    /** Comptes trouvés ; les identifiants inconnus sont absents de la map. Une seule requête. */
    Map<UUID, AccountSummary> findAll(Collection<UUID> userIds);

    /** Rejoint la transaction de l'appelant : profil et compte sont modifiés ensemble. */
    AccountSummary rename(UUID userId, String firstName, String lastName);
}
