package com.smartrecruit.application.domain;

import com.smartrecruit.application.api.ApplicationStatus;
import com.smartrecruit.application.api.CandidateStatus;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** US-28, US-34 : ce que le candidat voit de l'historique. */
class CandidateTimelineTest {

    private static final Instant T0 = Instant.parse("2026-10-14T08:20:00Z");

    @Test
    void internalStepsWithTheSameLabelAppearOnceAndNotesAreIgnored() {
        List<HistoryEntry> history = List.of(
                entry(HistoryEventType.STATUS_CHANGED, ApplicationStatus.SHORTLISTED, 2),
                entry(HistoryEventType.NOTE_ADDED, null, 3),
                entry(HistoryEventType.STATUS_CHANGED, ApplicationStatus.REJECTED, 4),
                entry(HistoryEventType.CREATED, ApplicationStatus.SUBMITTED, 0),
                entry(HistoryEventType.STATUS_CHANGED, ApplicationStatus.SCREENING, 1));

        assertThat(CandidateTimeline.of(history)).containsExactly(
                new CandidateTimeline.Step(CandidateStatus.SENT, T0),
                new CandidateTimeline.Step(CandidateStatus.IN_REVIEW, T0.plusSeconds(3600)),
                new CandidateTimeline.Step(CandidateStatus.NOT_SELECTED, T0.plusSeconds(4 * 3600)));
    }

    @Test
    void everyInternalStatusHasACandidateLabel() {
        assertThat(CandidateStatus.of(ApplicationStatus.SHORTLISTED)).isEqualTo(CandidateStatus.IN_REVIEW);
        assertThat(CandidateStatus.of(ApplicationStatus.REJECTED)).isEqualTo(CandidateStatus.NOT_SELECTED);
        assertThat(CandidateStatus.of(ApplicationStatus.OFFER)).isEqualTo(CandidateStatus.OFFER_RECEIVED);
    }

    private static HistoryEntry entry(HistoryEventType type, ApplicationStatus to, int hours) {
        return new HistoryEntry(UUID.randomUUID(), UUID.randomUUID(), type, null, to, null,
                Map.of("comment", "interne"), T0.plusSeconds(hours * 3600L));
    }
}
