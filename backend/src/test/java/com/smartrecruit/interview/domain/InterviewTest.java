package com.smartrecruit.interview.domain;

import com.smartrecruit.shared.domain.DomainException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.FieldValidationException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-35 à US-37 : règles d'un entretien, sans base ni Spring. */
class InterviewTest {

    private static final Instant NOW = Instant.parse("2026-10-22T09:00:00Z");
    private static final UUID RECRUITER = UUID.randomUUID();

    private static InterviewPlan plan(InterviewType type, String url, String address, Instant startsAt) {
        return new InterviewPlan(startsAt, 45, type, url, address, "Présentez votre parcours", Set.of(RECRUITER));
    }

    private static Interview scheduled() {
        return Interview.schedule(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), RECRUITER,
                plan(InterviewType.PHONE, null, null, NOW.plus(Duration.ofDays(2))));
    }

    @Test
    void locationDependsOnTheTypeAndAllErrorsAreReportedTogether() {
        assertThatThrownBy(() -> plan(InterviewType.VIDEO, "http://meet.example.com/x", null, NOW.minusSeconds(60))
                .validate(NOW))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.errors())
                        .extracting(FieldValidationException.FieldError::field)
                        .containsExactly("startsAt", "meetingUrl"));
        assertThatThrownBy(() -> plan(InterviewType.ON_SITE, null, "  ", NOW.plusSeconds(3600)).validate(NOW))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.errors())
                        .extracting(FieldValidationException.FieldError::field).containsExactly("address"));
        // Lien https pour VIDEO, adresse pour ON_SITE, rien d'exigé pour un appel
        plan(InterviewType.VIDEO, "https://meet.example.com/abc", null, NOW.plusSeconds(3600)).validate(NOW);
        plan(InterviewType.ON_SITE, null, "Twin Center, Casablanca", NOW.plusSeconds(3600)).validate(NOW);
        plan(InterviewType.PHONE, null, null, NOW.plusSeconds(3600)).validate(NOW);
    }

    @Test
    void durationAndParticipantsAreBounded() {
        InterviewPlan tooShort = new InterviewPlan(NOW.plusSeconds(3600), 10, InterviewType.PHONE, null, null, null,
                Set.of());
        assertThatThrownBy(() -> tooShort.validate(NOW))
                .isInstanceOfSatisfying(FieldValidationException.class, e -> assertThat(e.errors())
                        .extracting(FieldValidationException.FieldError::field)
                        .containsExactly("durationMinutes", "participantIds"));
    }

    @Test
    void rescheduleReturnsThePreviousDate() {
        Interview interview = scheduled();
        Instant previous = interview.getStartsAt();

        Instant returned = interview.reschedule(plan(InterviewType.PHONE, null, null, previous.plusSeconds(7200)));

        assertThat(returned).isEqualTo(previous);
        assertThat(interview.getStartsAt()).isEqualTo(previous.plusSeconds(7200));
    }

    /** US-37 */
    @Test
    void completionNeedsAParticipantAndAStartedInterview() {
        Interview interview = scheduled();

        assertThatThrownBy(() -> interview.complete(RECRUITER, FeedbackRating.POSITIVE, null, NOW))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATE);
        Instant later = interview.getStartsAt().plusSeconds(60);
        assertThatThrownBy(() -> interview.complete(UUID.randomUUID(), FeedbackRating.POSITIVE, null, later))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.ACCESS_DENIED);

        interview.complete(RECRUITER, FeedbackRating.POSITIVE, " Très bon échange ", later);
        assertThat(interview.getStatus()).isEqualTo(InterviewStatus.COMPLETED);
        assertThat(interview.getFeedbackComment()).isEqualTo("Très bon échange");
        assertThat(interview.getCompletedAt()).isEqualTo(later);
    }

    /** US-36 : COMPLETED et CANCELLED sont définitifs. */
    @Test
    void finishedInterviewsCannotChange() {
        Interview cancelled = scheduled();
        cancelled.cancel("Indisponibilité du candidat");
        assertThat(cancelled.getCancellationReason()).isEqualTo("Indisponibilité du candidat");

        assertThatThrownBy(() -> cancelled.reschedule(plan(InterviewType.PHONE, null, null, NOW.plusSeconds(99999))))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATE);
        assertThatThrownBy(() -> cancelled.cancel(null))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATE);
        assertThatThrownBy(() -> cancelled.complete(RECRUITER, FeedbackRating.NEUTRAL, null, NOW.plusSeconds(999999)))
                .extracting(e -> ((DomainException) e).code()).isEqualTo(ErrorCode.INVALID_STATE);
    }
}
