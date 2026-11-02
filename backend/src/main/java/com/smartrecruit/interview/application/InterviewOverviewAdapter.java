package com.smartrecruit.interview.application;

import com.smartrecruit.application.api.ApplicationInterviews;
import com.smartrecruit.application.api.CompletedInterviewChecker;
import com.smartrecruit.application.api.InterviewBrief;
import com.smartrecruit.interview.domain.InterviewStatus;
import com.smartrecruit.interview.infrastructure.InterviewRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Remplit les points d'extension déclarés par le module Candidatures : RG-33 (au moins un entretien
 * terminé pour passer à OFFER) et la liste des entretiens d'un dossier.
 */
@Component
@Transactional(readOnly = true)
class InterviewOverviewAdapter implements CompletedInterviewChecker, ApplicationInterviews {

    private final InterviewRepository interviews;

    InterviewOverviewAdapter(InterviewRepository interviews) {
        this.interviews = interviews;
    }

    @Override
    public boolean hasCompletedInterview(UUID applicationId) {
        return interviews.existsByApplicationIdAndStatus(applicationId, InterviewStatus.COMPLETED);
    }

    @Override
    public List<InterviewBrief> of(UUID applicationId) {
        return interviews.findByApplicationIdOrderByStartsAtAsc(applicationId).stream()
                .map(i -> new InterviewBrief(i.getId(), i.getStartsAt(), i.getDurationMinutes(), i.getType().name(),
                        i.getStatus().name()))
                .toList();
    }
}
