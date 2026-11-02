package com.smartrecruit.job.infrastructure;

import com.smartrecruit.job.domain.ContractType;
import com.smartrecruit.job.domain.ExperienceLevel;
import com.smartrecruit.job.domain.WorkMode;

import java.math.BigDecimal;
import java.util.Set;
import java.util.UUID;

/**
 * Filtres de la recherche publique (US-25), déjà normalisés : listes jamais nulles, page et taille bornées.
 *
 * @param skillIds l'offre exige ou souhaite toutes ces compétences
 * @param sort     tri reconnu ; un tri inconnu a déjà été remplacé par le tri par défaut
 */
public record JobSearchCriteria(String query, String city, String countryCode, Set<WorkMode> workModes,
                                Set<ContractType> contractTypes, Set<ExperienceLevel> experienceLevels,
                                UUID categoryId, Set<UUID> skillIds, BigDecimal salaryMin, JobSort sort,
                                int page, int size) {

    public JobSearchCriteria {
        workModes = workModes == null ? Set.of() : Set.copyOf(workModes);
        contractTypes = contractTypes == null ? Set.of() : Set.copyOf(contractTypes);
        experienceLevels = experienceLevels == null ? Set.of() : Set.copyOf(experienceLevels);
        skillIds = skillIds == null ? Set.of() : Set.copyOf(skillIds);
        sort = sort == null ? JobSort.DEFAULT : sort;
    }
}
