package com.smartrecruit.candidate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/**
 * Expérience professionnelle (US-14). Rattachée au profil par {@code candidateId} : chaque requête
 * la cherche avec l'identifiant du candidat connecté, ce qui rend celle d'un autre introuvable (RG-06).
 */
@Entity
@Table(name = "experience")
public class Experience {

    /** De la plus récente à la plus ancienne : postes en cours d'abord, puis date de fin, puis de début. */
    public static final Comparator<Experience> MOST_RECENT_FIRST = Comparator
            .comparing(Experience::getEndDate, Comparator.nullsFirst(Comparator.<LocalDate>reverseOrder()))
            .thenComparing(Experience::getStartDate, Comparator.reverseOrder());

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(name = "job_title", nullable = false, length = 120)
    private String jobTitle;

    @Column(name = "company_name", nullable = false, length = 120)
    private String companyName;

    @Column(length = 80)
    private String city;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @Column(length = 2000)
    private String description;

    protected Experience() {
        // JPA
    }

    public static Experience create(UUID candidateId, ExperienceDetails details) {
        Experience experience = new Experience();
        experience.candidateId = Objects.requireNonNull(candidateId, "candidateId");
        experience.update(details);
        return experience;
    }

    public void update(ExperienceDetails details) {
        if (details.endDate() != null && details.endDate().isBefore(details.startDate())) {
            // Garanti en amont par @ChronologicalOrder ; filet de sécurité avant la contrainte SQL
            throw new IllegalArgumentException("Date de fin antérieure à la date de début");
        }
        this.jobTitle = details.jobTitle().strip();
        this.companyName = details.companyName().strip();
        this.city = blankToNull(details.city());
        this.startDate = details.startDate();
        this.endDate = details.endDate();
        this.description = blankToNull(details.description());
    }

    /** Poste en cours : pas de date de fin. */
    public boolean isCurrent() {
        return endDate == null;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getJobTitle() {
        return jobTitle;
    }

    public String getCompanyName() {
        return companyName;
    }

    public String getCity() {
        return city;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public LocalDate getEndDate() {
        return endDate;
    }

    public String getDescription() {
        return description;
    }
}
