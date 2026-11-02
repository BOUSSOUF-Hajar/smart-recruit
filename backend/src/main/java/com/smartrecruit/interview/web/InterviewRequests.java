package com.smartrecruit.interview.web;

import com.smartrecruit.interview.domain.FeedbackRating;
import com.smartrecruit.interview.domain.InterviewPlan;
import com.smartrecruit.interview.domain.InterviewType;
import com.smartrecruit.shared.validation.HttpsUrl;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/** Corps des requêtes d'entretien ({@code docs/api.md} §8). */
final class InterviewRequests {

    private InterviewRequests() {
    }

    /**
     * Création et modification. {@code startsAt} porte son décalage ({@code Z} ou {@code +01:00}) : le
     * frontend envoie l'heure locale saisie convertie en UTC (RG-32) ; le serveur stocke un instant.
     * {@code version} : en modification seulement.
     */
    record InterviewRequest(
            @NotNull OffsetDateTime startsAt,
            @NotNull @Min(InterviewPlan.MIN_DURATION) @Max(InterviewPlan.MAX_DURATION) Integer durationMinutes,
            @NotNull InterviewType type,
            @Size(max = 500) @HttpsUrl String meetingUrl,
            @Size(max = 255) String address,
            @Size(max = 1000) String candidateInstructions,
            @NotEmpty @Size(max = InterviewPlan.MAX_PARTICIPANTS) List<@NotNull UUID> participantIds,
            Long version) {

        InterviewPlan toPlan() {
            return new InterviewPlan(startsAt.toInstant(), durationMinutes, type, meetingUrl, address, candidateInstructions,
                    new HashSet<>(participantIds));
        }
    }

    record CancelRequest(@Size(max = 500) String reason) {
    }

    record CompleteRequest(@NotNull FeedbackRating rating, @Size(max = 2000) String comment) {
    }
}
