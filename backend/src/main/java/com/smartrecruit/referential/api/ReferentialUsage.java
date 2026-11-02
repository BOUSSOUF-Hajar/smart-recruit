package com.smartrecruit.referential.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Utilisation des catégories et des compétences par les autres modules (US-50, US-51, RG-30).
 * <p>
 * Implémentée par chaque module qui y fait référence (Offres, Candidats) : Référentiels additionne
 * les réponses sans connaître leurs tables. Chaque méthode rejoint la transaction de l'appelant.
 */
public interface ReferentialUsage {

    /** Nombre de références par catégorie ; une catégorie inutilisée est absente. */
    default Map<UUID, Long> categoryUsage(Collection<UUID> categoryIds) {
        return Map.of();
    }

    /** Nombre de références par compétence (profils, offres) ; une compétence inutilisée est absente. */
    default Map<UUID, Long> skillUsage(Collection<UUID> skillIds) {
        return Map.of();
    }

    /**
     * Fusion (US-51) : reporte les références de {@code sourceId} sur {@code targetId}. Quand une même
     * ressource avait les deux, elle n'en garde qu'une, avec l'exigence la plus forte.
     *
     * @return nombre de références reportées ou fusionnées
     */
    default int reassignSkill(UUID sourceId, UUID targetId) {
        return 0;
    }
}
