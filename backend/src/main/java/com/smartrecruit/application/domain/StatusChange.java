package com.smartrecruit.application.domain;

import com.smartrecruit.application.api.ApplicationStatus;

/** Résultat d'une transition appliquée. */
public record StatusChange(ApplicationStatus from, ApplicationStatus to) {
}
