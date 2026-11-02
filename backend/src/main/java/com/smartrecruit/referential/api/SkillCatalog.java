package com.smartrecruit.referential.api;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Lecture du référentiel de compétences par les autres modules (profil candidat, offres, IA),
 * sans accès à ses tables ni à ses entités.
 */
public interface SkillCatalog {

    /** Compétences trouvées, actives ou non ; les identifiants inconnus sont absents de la map. */
    Map<UUID, SkillSummary> findByIds(Collection<UUID> ids);

    /**
     * Rattache des noms libres (extraits d'un CV par l'IA) au référentiel, par nom normalisé ou par
     * alias (« springboot » → Spring Boot). Clé : le nom tel que reçu ; les noms inconnus sont absents.
     */
    Map<String, SkillSummary> resolve(Collection<String> rawNames);
}
