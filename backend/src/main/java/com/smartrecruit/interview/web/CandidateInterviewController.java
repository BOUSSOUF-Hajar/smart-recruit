package com.smartrecruit.interview.web;

import com.smartrecruit.interview.application.InterviewQueryService;
import com.smartrecruit.interview.web.InterviewResponses.CandidateInterviewResponse;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** US-38 : entretiens du candidat connecté, à venir ({@code upcoming=true}, défaut) ou passés. */
@RestController
class CandidateInterviewController {

    private final InterviewQueryService queries;

    CandidateInterviewController(InterviewQueryService queries) {
        this.queries = queries;
    }

    @GetMapping("/api/v1/candidates/me/interviews")
    List<CandidateInterviewResponse> mine(CurrentUser candidate, @RequestParam(defaultValue = "true") boolean upcoming) {
        return queries.candidateInterviews(candidate, upcoming).stream().map(InterviewResponses::candidate).toList();
    }
}
