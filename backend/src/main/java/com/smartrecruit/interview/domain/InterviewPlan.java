package com.smartrecruit.interview.domain;

import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.FieldValidationException.FieldError;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Ce que le recruteur saisit pour planifier ou reprogrammer un entretien (F-INT-01, F-INT-02).
 * Les règles qui dépendent de plusieurs champs (lieu selon le type) ou du temps (date future) sont
 * vérifiées ici ; la base les répète en contraintes CHECK.
 */
public record InterviewPlan(Instant startsAt, int durationMinutes, InterviewType type, String meetingUrl,
                            String address, String candidateInstructions, Set<UUID> participantIds) {

    public static final int MIN_DURATION = 15;
    public static final int MAX_DURATION = 240;
    public static final int MAX_PARTICIPANTS = 10;

    public InterviewPlan {
        meetingUrl = blankToNull(meetingUrl);
        address = blankToNull(address);
        candidateInstructions = blankToNull(candidateInstructions);
        participantIds = participantIds == null ? Set.of() : Set.copyOf(new LinkedHashSet<>(participantIds));
    }

    /** Toutes les erreurs ensemble, chacune sous son champ. */
    public void validate(Instant now) {
        List<FieldError> errors = new ArrayList<>();
        if (startsAt == null || !startsAt.isAfter(now)) {
            errors.add(new FieldError("startsAt", "doit être dans le futur"));
        }
        if (durationMinutes < MIN_DURATION || durationMinutes > MAX_DURATION) {
            errors.add(new FieldError("durationMinutes", "doit être compris entre 15 et 240 minutes"));
        }
        if (type == InterviewType.VIDEO && (meetingUrl == null || !meetingUrl.startsWith("https://"))) {
            errors.add(new FieldError("meetingUrl", "un lien https est obligatoire pour une visioconférence"));
        }
        if (type == InterviewType.ON_SITE && address == null) {
            errors.add(new FieldError("address", "l'adresse est obligatoire pour un entretien sur place"));
        }
        if (participantIds.isEmpty() || participantIds.size() > MAX_PARTICIPANTS) {
            errors.add(new FieldError("participantIds", "entre 1 et 10 participants"));
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }
}
