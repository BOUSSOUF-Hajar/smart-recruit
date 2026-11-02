package com.smartrecruit.candidate.domain;

import java.util.Objects;

public record EducationDetails(String degree, String school, String fieldOfStudy, Integer startYear,
                               Integer endYear) {

    public EducationDetails {
        Objects.requireNonNull(degree, "degree");
        Objects.requireNonNull(school, "school");
    }
}
