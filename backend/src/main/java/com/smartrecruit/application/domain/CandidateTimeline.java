package com.smartrecruit.application.domain;

import com.smartrecruit.application.api.CandidateStatus;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Chronologie vue par le candidat (US-28, US-34) : seulement les statuts, en libellés simplifiés,
 * du plus ancien au plus récent. Deux étapes internes qui donnent le même libellé (SCREENING puis
 * SHORTLISTED) n'apparaissent qu'une fois. Ni motif, ni commentaire, ni auteur.
 */
public final class CandidateTimeline {

    private CandidateTimeline() {
    }

    public record Step(CandidateStatus candidateStatus, Instant at) {
    }

    public static List<Step> of(List<HistoryEntry> history) {
        List<Step> steps = new ArrayList<>();
        history.stream()
                .filter(entry -> entry.toStatus() != null)
                .sorted(Comparator.comparing(HistoryEntry::occurredAt).thenComparing(HistoryEntry::id))
                .forEach(entry -> {
                    CandidateStatus status = CandidateStatus.of(entry.toStatus());
                    if (steps.isEmpty() || steps.getLast().candidateStatus() != status) {
                        steps.add(new Step(status, entry.occurredAt()));
                    }
                });
        return List.copyOf(steps);
    }
}
