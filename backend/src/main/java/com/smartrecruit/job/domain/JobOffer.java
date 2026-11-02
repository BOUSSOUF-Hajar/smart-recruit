package com.smartrecruit.job.domain;

import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.persistence.VersionedEntity;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.Embedded;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/**
 * Offre d'emploi et son cycle de vie (analyse fonctionnelle §4.2) :
 * <pre>
 * DRAFT → PUBLISHED (publier) → CLOSED (clôturer) → ARCHIVED (archiver)
 * PUBLISHED → DRAFT si aucune candidature ; CLOSED → PUBLISHED si nouvelle expiration future
 * </pre>
 * Toute autre transition est refusée ({@code 409 INVALID_STATUS_TRANSITION}). Le statut ne change
 * que par ces méthodes : jamais de modification libre du champ.
 */
@Entity
@Table(name = "job_offer")
public class JobOffer extends VersionedEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "company_id", nullable = false, updatable = false)
    private UUID companyId;

    @Column(name = "created_by", nullable = false, updatable = false)
    private UUID createdBy;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(nullable = false, length = 150)
    private String title;

    @Column(nullable = false, columnDefinition = "text")
    private String description;

    @Column(nullable = false, length = 80)
    private String city;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "country_code", nullable = false, length = 2)
    private String countryCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "work_mode", nullable = false, length = 16)
    private WorkMode workMode;

    @Enumerated(EnumType.STRING)
    @Column(name = "contract_type", nullable = false, length = 16)
    private ContractType contractType;

    @Enumerated(EnumType.STRING)
    @Column(name = "experience_level", nullable = false, length = 8)
    private ExperienceLevel experienceLevel;

    @Column(name = "min_years_experience", nullable = false)
    private short minYearsExperience;

    @Embedded
    private Salary salary = Salary.none();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private JobStatus status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @ElementCollection
    @CollectionTable(name = "job_offer_skill", joinColumns = @JoinColumn(name = "job_offer_id"))
    private List<JobSkill> skills = new ArrayList<>();

    protected JobOffer() {
        // JPA
    }

    /** US-21 : toujours créée en brouillon, dans l'entreprise du recruteur (RG-05). */
    public static JobOffer draft(UUID companyId, UUID createdBy, JobContent content) {
        JobOffer offer = new JobOffer();
        offer.companyId = Objects.requireNonNull(companyId, "companyId");
        offer.createdBy = Objects.requireNonNull(createdBy, "createdBy");
        offer.status = JobStatus.DRAFT;
        offer.apply(content);
        return offer;
    }

    /** US-22 : modifiable en DRAFT et PUBLISHED, en lecture seule ensuite (RG-17). */
    public void update(JobContent content) {
        if (status != JobStatus.DRAFT && status != JobStatus.PUBLISHED) {
            throw new InvalidStateException(ErrorCode.INVALID_STATE,
                    "Une offre clôturée ou archivée ne peut plus être modifiée.");
        }
        if (status == JobStatus.PUBLISHED && content.skills().stream().noneMatch(JobSkill::isRequired)) {
            throw publicationRefused(List.of("NO_REQUIRED_SKILL"));
        }
        apply(content);
    }

    /** US-23 : publication avec une expiration future d'au plus 180 jours et au moins une compétence requise. */
    public void publish(LocalDate expiryDate, ZoneId zone, Instant now) {
        requireStatus(JobStatus.DRAFT, "publier");
        checkPublishable(expiryDate, zone, now);
        status = JobStatus.PUBLISHED;
        publishedAt = now;
        expiresAt = PublicationRules.expiryInstant(expiryDate, zone);
    }

    /** Retour en brouillon, seulement si personne n'a encore postulé. */
    public void unpublish(boolean hasApplications) {
        requireStatus(JobStatus.PUBLISHED, "repasser en brouillon");
        if (hasApplications) {
            throw new InvalidStateException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Cette offre a déjà reçu des candidatures : clôturez-la plutôt.");
        }
        status = JobStatus.DRAFT;
        publishedAt = null;
        expiresAt = null;
    }

    /** Plus de nouvelles candidatures ; celles en cours continuent. */
    public void close() {
        requireStatus(JobStatus.PUBLISHED, "clôturer");
        status = JobStatus.CLOSED;
    }

    /** Réouverture avec une nouvelle expiration ; la date de première publication est conservée. */
    public void reopen(LocalDate expiryDate, ZoneId zone, Instant now) {
        requireStatus(JobStatus.CLOSED, "rouvrir");
        checkPublishable(expiryDate, zone, now);
        status = JobStatus.PUBLISHED;
        expiresAt = PublicationRules.expiryInstant(expiryDate, zone);
    }

    public void archive() {
        requireStatus(JobStatus.CLOSED, "archiver");
        status = JobStatus.ARCHIVED;
    }

    /** RG-18 : seul un brouillon disparaît ; une offre déjà publiée se clôture puis s'archive. */
    public void checkDeletable() {
        if (status != JobStatus.DRAFT) {
            throw new InvalidStateException(ErrorCode.RESOURCE_IN_USE,
                    "Seul un brouillon peut être supprimé : clôturez puis archivez cette offre.");
        }
    }

    /** Visible du public et ouverte aux candidatures (RG-11, RG-19). */
    public boolean isOpen(Instant now) {
        return status == JobStatus.PUBLISHED && expiresAt != null && expiresAt.isAfter(now);
    }

    /** Publiée mais date dépassée : masquée sans changer de statut (V1). */
    public boolean isExpired(Instant now) {
        return status == JobStatus.PUBLISHED && !isOpen(now);
    }

    private void apply(JobContent content) {
        this.title = content.title().strip();
        this.description = content.description().strip();
        this.categoryId = content.categoryId();
        this.city = content.city().strip();
        this.countryCode = content.countryCode().strip().toUpperCase(Locale.ROOT);
        this.workMode = content.workMode();
        this.contractType = content.contractType();
        this.experienceLevel = content.experienceLevel();
        this.minYearsExperience = (short) content.minYearsExperience();
        this.salary = content.salary();
        this.skills.clear();
        this.skills.addAll(content.skills());
    }

    private void checkPublishable(LocalDate expiryDate, ZoneId zone, Instant now) {
        List<String> reasons = PublicationRules.blockingReasons(expiryDate, zone, now,
                skills.stream().anyMatch(JobSkill::isRequired));
        if (!reasons.isEmpty()) {
            throw publicationRefused(reasons);
        }
    }

    private static BusinessRuleViolationException publicationRefused(List<String> reasons) {
        return new BusinessRuleViolationException(ErrorCode.JOB_NOT_PUBLISHABLE,
                "L'offre ne peut pas être publiée en l'état.", Map.of("reasons", reasons));
    }

    private void requireStatus(JobStatus expected, String action) {
        if (status != expected) {
            throw new InvalidStateException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Impossible de " + action + " une offre " + status + ".",
                    Map.of("currentStatus", status.name(), "allowedFrom", Set.of(expected.name())));
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public UUID getCategoryId() {
        return categoryId;
    }

    public String getTitle() {
        return title;
    }

    public String getDescription() {
        return description;
    }

    public String getCity() {
        return city;
    }

    public String getCountryCode() {
        return countryCode;
    }

    public WorkMode getWorkMode() {
        return workMode;
    }

    public ContractType getContractType() {
        return contractType;
    }

    public ExperienceLevel getExperienceLevel() {
        return experienceLevel;
    }

    public int getMinYearsExperience() {
        return minYearsExperience;
    }

    public Salary getSalary() {
        return salary;
    }

    public JobStatus getStatus() {
        return status;
    }

    public Instant getPublishedAt() {
        return publishedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public List<JobSkill> getSkills() {
        return List.copyOf(skills);
    }
}
