package com.smartrecruit.job.infrastructure;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Ligne de résultat de la recherche : seulement les colonnes affichées dans la liste. */
public record JobSearchRow(UUID id, UUID companyId, String title, String city, String countryCode, String workMode,
                           String contractType, String experienceLevel, BigDecimal salaryMin, BigDecimal salaryMax,
                           String salaryCurrency, String salaryPeriod, boolean salaryVisible, Instant publishedAt,
                           Instant expiresAt) {
}
