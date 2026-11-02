package com.smartrecruit.interview.web;

import com.smartrecruit.application.api.ApplicationRef;
import com.smartrecruit.interview.application.InterviewQueryService.CandidateInterview;
import com.smartrecruit.interview.application.InterviewQueryService.Person;
import com.smartrecruit.interview.application.InterviewQueryService.RecruiterInterview;
import com.smartrecruit.interview.domain.FeedbackRating;
import com.smartrecruit.interview.domain.Interview;
import com.smartrecruit.interview.domain.InterviewStatus;
import com.smartrecruit.interview.domain.InterviewType;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Deux vues sans type commun : celle du candidat ne peut pas contenir l'avis ni les participants,
 * même par erreur de sérialisation (RG-23).
 */
final class InterviewResponses {

    private InterviewResponses() {
    }

    record RecruiterInterviewResponse(UUID id, ApplicationBrief application, Instant startsAt, int durationMinutes,
                                      InterviewType type, String meetingUrl, String address,
                                      String candidateInstructions, InterviewStatus status,
                                      String cancellationReason, Feedback feedback, List<Person> participants,
                                      Person scheduledBy, long version) {
    }

    record ApplicationBrief(UUID id, String candidateName, UUID jobOfferId, String jobTitle) {
    }

    record Feedback(FeedbackRating rating, String comment, Instant completedAt) {
    }

    static RecruiterInterviewResponse recruiter(RecruiterInterview view) {
        Interview i = view.interview();
        ApplicationRef ref = view.application();
        return new RecruiterInterviewResponse(i.getId(),
                ref == null ? new ApplicationBrief(i.getApplicationId(), "", i.getJobOfferId(), "")
                        : new ApplicationBrief(ref.id(), ref.candidateName(), ref.jobOfferId(), ref.jobTitle()),
                i.getStartsAt(), i.getDurationMinutes(), i.getType(), i.getMeetingUrl(), i.getAddress(),
                i.getCandidateInstructions(), i.getStatus(), i.getCancellationReason(),
                i.getFeedbackRating() == null ? null
                        : new Feedback(i.getFeedbackRating(), i.getFeedbackComment(), i.getCompletedAt()),
                view.participants(), view.scheduledBy(), i.getVersion() == null ? 0 : i.getVersion());
    }

    /** Vue candidat (docs/api.md §5) : de quoi se préparer, rien de l'évaluation. */
    record CandidateInterviewResponse(UUID id, String jobTitle, String companyName, Instant startsAt,
                                      int durationMinutes, InterviewType type, String meetingUrl, String address,
                                      String candidateInstructions, InterviewStatus status) {
    }

    static CandidateInterviewResponse candidate(CandidateInterview view) {
        Interview i = view.interview();
        return new CandidateInterviewResponse(i.getId(), view.jobTitle(), view.companyName(), i.getStartsAt(),
                i.getDurationMinutes(), i.getType(), i.getMeetingUrl(), i.getAddress(), i.getCandidateInstructions(),
                i.getStatus());
    }
}
