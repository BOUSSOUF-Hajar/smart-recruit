package com.smartrecruit.candidate.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.util.Comparator;
import java.util.Objects;
import java.util.UUID;

/** Formation (US-14) ; années seules, sans mois ni jour. */
@Entity
@Table(name = "education")
public class Education {

    /** Formations en cours (sans année de fin) d'abord, puis de la plus récente à la plus ancienne. */
    public static final Comparator<Education> MOST_RECENT_FIRST = Comparator
            .comparing(Education::getEndYear, Comparator.nullsFirst(Comparator.<Integer>reverseOrder()))
            .thenComparing(Education::getStartYear, Comparator.nullsLast(Comparator.<Integer>reverseOrder()));

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(name = "candidate_id", nullable = false, updatable = false)
    private UUID candidateId;

    @Column(nullable = false, length = 120)
    private String degree;

    @Column(nullable = false, length = 120)
    private String school;

    @Column(name = "field_of_study", length = 120)
    private String fieldOfStudy;

    @Column(name = "start_year")
    private Short startYear;

    @Column(name = "end_year")
    private Short endYear;

    protected Education() {
        // JPA
    }

    public static Education create(UUID candidateId, EducationDetails details) {
        Education education = new Education();
        education.candidateId = Objects.requireNonNull(candidateId, "candidateId");
        education.update(details);
        return education;
    }

    public void update(EducationDetails details) {
        if (details.startYear() != null && details.endYear() != null && details.endYear() < details.startYear()) {
            throw new IllegalArgumentException("Année de fin antérieure à l'année de début");
        }
        this.degree = details.degree().strip();
        this.school = details.school().strip();
        this.fieldOfStudy = details.fieldOfStudy() == null || details.fieldOfStudy().isBlank()
                ? null : details.fieldOfStudy().strip();
        this.startYear = details.startYear() == null ? null : details.startYear().shortValue();
        this.endYear = details.endYear() == null ? null : details.endYear().shortValue();
    }

    public UUID getId() {
        return id;
    }

    public UUID getCandidateId() {
        return candidateId;
    }

    public String getDegree() {
        return degree;
    }

    public String getSchool() {
        return school;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public Integer getStartYear() {
        return startYear == null ? null : startYear.intValue();
    }

    public Integer getEndYear() {
        return endYear == null ? null : endYear.intValue();
    }
}
