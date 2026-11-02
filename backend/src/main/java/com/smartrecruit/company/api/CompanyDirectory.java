package com.smartrecruit.company.api;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Lecture des entreprises par les autres modules (offres, candidatures), sans accès à leurs tables. */
public interface CompanyDirectory {

    Optional<CompanySummary> find(UUID companyId);

    /** Entreprises trouvées ; les identifiants inconnus sont absents de la map. */
    Map<UUID, CompanySummary> findAll(Collection<UUID> companyIds);

    /** Recruteurs rattachés à l'entreprise, triés par nom (participants d'entretien, US-35). */
    List<RecruiterSummary> recruitersOf(UUID companyId);
}
