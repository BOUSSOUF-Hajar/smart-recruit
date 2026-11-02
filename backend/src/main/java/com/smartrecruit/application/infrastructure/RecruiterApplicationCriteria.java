package com.smartrecruit.application.infrastructure;

import com.smartrecruit.application.api.ApplicationStatus;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;

/**
 * Filtres de la liste recruteur (docs/api.md §7).
 *
 * @param submittedFrom   inclus
 * @param submittedBefore exclu (lendemain de la date « au » saisie)
 */
public record RecruiterApplicationCriteria(UUID companyId, UUID jobOfferId, Set<ApplicationStatus> statuses,
                                           Set<UUID> skillIds, Instant submittedFrom, Instant submittedBefore,
                                           String candidateName, Integer minScore, ApplicationSort sort,
                                           int page, int size) {

    public RecruiterApplicationCriteria {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
        skillIds = skillIds == null ? Set.of() : Set.copyOf(skillIds);
        sort = sort == null ? ApplicationSort.SUBMITTED_AT : sort;
    }
}
