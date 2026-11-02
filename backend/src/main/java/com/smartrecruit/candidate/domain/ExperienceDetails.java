package com.smartrecruit.candidate.domain;

import java.time.LocalDate;
import java.util.Objects;

/** Contenu d'une expérience, validé par la couche web avant d'arriver ici. */
public record ExperienceDetails(String jobTitle, String companyName, String city, LocalDate startDate,
                                LocalDate endDate, String description) {

    public ExperienceDetails {
        Objects.requireNonNull(jobTitle, "jobTitle");
        Objects.requireNonNull(companyName, "companyName");
        Objects.requireNonNull(startDate, "startDate");
    }
}
