package com.smartrecruit.job.application;

import com.smartrecruit.company.api.CompanySummary;
import com.smartrecruit.job.domain.ContractType;
import com.smartrecruit.job.domain.JobOffer;
import com.smartrecruit.job.domain.JobStatus;
import com.smartrecruit.job.infrastructure.JobSearchRow;
import com.smartrecruit.referential.api.CategorySummary;
import com.smartrecruit.referential.api.SkillLevel;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Vues assemblées dans la transaction de lecture : l'offre et les noms lus dans les autres modules
 * (entreprise, catégorie, compétences). Le contrôleur ne déclenche aucun chargement.
 */
public final class JobViews {

    private JobViews() {
    }

    /** @param minLevel niveau minimum pour une compétence requise, {@code null} pour une souhaitée */
    public record SkillRef(UUID id, String name, SkillLevel minLevel) {
    }

    public record RecruiterJob(JobOffer offer, List<SkillRef> requiredSkills, List<SkillRef> preferredSkills,
                               String createdByName, Map<String, Long> applicationCounts, boolean expired) {
    }

    public record RecruiterJobSummary(UUID id, String title, JobStatus status, String city,
                                      ContractType contractType, Instant publishedAt, Instant expiresAt,
                                      boolean expired, Map<String, Long> applicationCounts, Instant updatedAt) {
    }

    public record JobCard(JobSearchRow row, String companyName, List<String> topSkills) {
    }

    /**
     * @param alreadyApplied  pour un candidat connecté ; {@code null} pour les autres visiteurs
     * @param myApplicationId candidature du candidat connecté à cette offre, s'il a postulé
     */
    public record JobDetail(JobOffer offer, CompanySummary company, CategorySummary category,
                            List<SkillRef> requiredSkills, List<SkillRef> preferredSkills, Boolean alreadyApplied,
                            UUID myApplicationId) {
    }
}
