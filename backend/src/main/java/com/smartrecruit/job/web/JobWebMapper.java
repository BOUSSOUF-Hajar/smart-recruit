package com.smartrecruit.job.web;

import com.smartrecruit.job.application.JobViews.JobCard;
import com.smartrecruit.job.application.JobViews.JobDetail;
import com.smartrecruit.job.application.JobViews.RecruiterJob;
import com.smartrecruit.job.application.JobViews.RecruiterJobSummary;
import com.smartrecruit.job.application.JobViews.SkillRef;
import com.smartrecruit.job.domain.JobOffer;
import com.smartrecruit.job.domain.Salary;
import com.smartrecruit.job.domain.SalaryPeriod;
import com.smartrecruit.job.infrastructure.JobSearchRow;
import com.smartrecruit.job.web.JobResponses.JobCardResponse;
import com.smartrecruit.job.web.JobResponses.JobDetailResponse;
import com.smartrecruit.job.web.JobResponses.LocationResponse;
import com.smartrecruit.job.web.JobResponses.PublicSalary;
import com.smartrecruit.job.web.JobResponses.RecruiterJobResponse;
import com.smartrecruit.job.web.JobResponses.RecruiterJobSummaryResponse;
import com.smartrecruit.job.web.JobResponses.RecruiterSalary;
import com.smartrecruit.job.web.JobResponses.RecruiterSkillResponse;
import com.smartrecruit.job.web.JobResponses.Ref;
import com.smartrecruit.job.web.JobResponses.RequiredSkillResponse;
import com.smartrecruit.job.domain.ContractType;
import com.smartrecruit.job.domain.ExperienceLevel;
import com.smartrecruit.job.domain.WorkMode;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

/**
 * Vues → DTO. MapStruct génère les correspondances directes (résumé recruteur, compétences) ;
 * les règles d'affichage (salaire public ou non, entreprise) restent écrites à la main.
 */
@Mapper
interface JobWebMapper {

    RecruiterJobSummaryResponse toResponse(RecruiterJobSummary summary);

    @Mapping(target = "skillId", source = "id")
    RecruiterSkillResponse toRecruiterSkill(SkillRef skill);

    List<RecruiterSkillResponse> toRecruiterSkills(List<SkillRef> skills);

    RequiredSkillResponse toRequiredSkill(SkillRef skill);

    List<RequiredSkillResponse> toRequiredSkills(List<SkillRef> skills);

    Ref toRef(SkillRef skill);

    List<Ref> toRefs(List<SkillRef> skills);

    default RecruiterJobResponse toResponse(RecruiterJob view) {
        JobOffer offer = view.offer();
        Salary salary = offer.getSalary();
        return new RecruiterJobResponse(offer.getId(), offer.getTitle(), offer.getDescription(), offer.getCategoryId(),
                new LocationResponse(offer.getCity(), offer.getCountryCode()), offer.getWorkMode(),
                offer.getContractType(), offer.getExperienceLevel(), offer.getMinYearsExperience(),
                salary.isDefined() ? new RecruiterSalary(salary.getMin(), salary.getMax(), salary.getCurrency(),
                        salary.getPeriod(), salary.isVisible()) : null,
                toRecruiterSkills(view.requiredSkills()), toRecruiterSkills(view.preferredSkills()),
                offer.getStatus(), offer.getPublishedAt(), offer.getExpiresAt(), view.expired(),
                new Ref(offer.getCreatedBy(), view.createdByName()), view.applicationCounts(),
                offer.getVersion() == null ? 0 : offer.getVersion());
    }

    default JobCardResponse toResponse(JobCard card) {
        JobSearchRow row = card.row();
        PublicSalary salary = row.salaryVisible() && (row.salaryMin() != null || row.salaryMax() != null)
                ? new PublicSalary(row.salaryMin(), row.salaryMax(), row.salaryCurrency(),
                        row.salaryPeriod() == null ? null : SalaryPeriod.valueOf(row.salaryPeriod()))
                : null;
        return new JobCardResponse(row.id(), row.title(), new Ref(row.companyId(), card.companyName()),
                new LocationResponse(row.city(), row.countryCode()), WorkMode.valueOf(row.workMode()),
                ContractType.valueOf(row.contractType()), ExperienceLevel.valueOf(row.experienceLevel()), salary,
                card.topSkills(), row.publishedAt(), row.expiresAt());
    }

    default JobDetailResponse toResponse(JobDetail detail) {
        JobOffer offer = detail.offer();
        Salary salary = offer.getSalary();
        PublicSalary publicSalary = salary.isVisible() && salary.isDefined()
                ? new PublicSalary(salary.getMin(), salary.getMax(), salary.getCurrency(), salary.getPeriod())
                : null;
        return new JobDetailResponse(offer.getId(), offer.getTitle(), offer.getDescription(),
                new Ref(detail.company().id(), detail.company().name()),
                new Ref(detail.category().id(), detail.category().name()),
                new LocationResponse(offer.getCity(), offer.getCountryCode()), offer.getWorkMode(),
                offer.getContractType(), offer.getExperienceLevel(), offer.getMinYearsExperience(), publicSalary,
                toRequiredSkills(detail.requiredSkills()), toRefs(detail.preferredSkills()), offer.getPublishedAt(),
                offer.getExpiresAt(), detail.alreadyApplied(), detail.myApplicationId());
    }
}
