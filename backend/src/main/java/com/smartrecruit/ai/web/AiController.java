package com.smartrecruit.ai.web;

import com.smartrecruit.ai.application.AIService;
import com.smartrecruit.ai.application.AiTaskService;
import com.smartrecruit.ai.domain.AiTask;
import com.smartrecruit.ai.domain.AiTaskStatus;
import com.smartrecruit.ai.domain.AiTaskType;
import com.smartrecruit.ai.domain.AnalysisDocument;
import com.smartrecruit.ai.domain.ResumeExtraction;
import com.smartrecruit.ai.domain.matching.MatchLabel;
import com.smartrecruit.ai.domain.matching.MatchingResult;
import com.smartrecruit.ai.infrastructure.AiResultRepository.StoredAnalysis;
import com.smartrecruit.ai.infrastructure.AiResultRepository.StoredMatch;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Assistance IA ({@code docs/api.md} §9), réservée aux recruteurs, sur les candidatures de leur entreprise. */
@RestController
class AiController {

    static final String DISCLAIMER = "Indicateur d'aide à la décision. Il ne remplace pas l'évaluation du recruteur.";

    private final AiTaskService service;
    private final AIService ai;

    AiController(AiTaskService service, AIService ai) {
        this.service = service;
        this.ai = ai;
    }

    /** Le frontend masque les boutons IA si l'assistance est désactivée (D7.3). */
    @GetMapping("/api/v1/ai/status")
    StatusResponse status() {
        return new StatusResponse(service.enabled(), service.enabled() ? ai.modelName() : null);
    }

    /** US-41 : 202 et l'identifiant de tâche, aussitôt. */
    @PostMapping("/api/v1/applications/{applicationId}/ai/resume-analysis")
    ResponseEntity<TaskResponse> analyse(CurrentUser recruiter, @PathVariable UUID applicationId) {
        return accepted(service.request(recruiter, applicationId, AiTaskType.RESUME_ANALYSIS));
    }

    /** US-42 : l'analyse du CV est faite d'abord si nécessaire. */
    @PostMapping("/api/v1/applications/{applicationId}/ai/matching")
    ResponseEntity<TaskResponse> match(CurrentUser recruiter, @PathVariable UUID applicationId) {
        return accepted(service.request(recruiter, applicationId, AiTaskType.MATCHING));
    }

    /** Interrogé toutes les 3 s ; en DONE, {@code Location} mène au résultat. */
    @GetMapping("/api/v1/ai/tasks/{taskId}")
    ResponseEntity<TaskResponse> task(CurrentUser recruiter, @PathVariable UUID taskId) {
        AiTask task = service.task(recruiter, taskId);
        var response = ResponseEntity.ok();
        if (task.getStatus() == AiTaskStatus.DONE) {
            response.location(URI.create(resultPath(task)));
        }
        return response.body(TaskResponse.of(task));
    }

    @GetMapping("/api/v1/applications/{applicationId}/ai/resume-analysis")
    AnalysisResponse analysis(CurrentUser recruiter, @PathVariable UUID applicationId) {
        StoredAnalysis stored = service.latestAnalysis(recruiter, applicationId);
        AnalysisDocument d = stored.document();
        return new AnalysisResponse(d.skills(), d.experiences(), d.educations(), d.languages(), d.summary(),
                new Model(stored.modelName(), stored.promptVersion()), stored.createdAt(), DISCLAIMER);
    }

    @GetMapping("/api/v1/applications/{applicationId}/ai/matching")
    MatchResponse matching(CurrentUser recruiter, @PathVariable UUID applicationId) {
        StoredMatch m = service.latestMatch(recruiter, applicationId);
        return new MatchResponse(m.score(), MatchLabel.of(m.score()),
                new Breakdown(m.requiredCoverage(), m.preferredCoverage(), m.experienceFit(), m.formulaVersion()),
                m.document().matched(), m.document().missing(), m.document().strengths(), m.document().weaknesses(),
                m.document().summary(), m.document().warnings(), DISCLAIMER,
                new Model(m.modelName(), m.promptVersion()), m.createdAt());
    }

    private static ResponseEntity<TaskResponse> accepted(AiTask task) {
        return ResponseEntity.accepted().location(URI.create("/api/v1/ai/tasks/" + task.getId()))
                .body(TaskResponse.of(task));
    }

    private static String resultPath(AiTask task) {
        return "/api/v1/applications/" + task.getApplicationId() + "/ai/"
                + (task.getType() == AiTaskType.MATCHING ? "matching" : "resume-analysis");
    }

    record StatusResponse(boolean enabled, String model) {
    }

    record TaskResponse(UUID id, AiTaskType type, UUID applicationId, AiTaskStatus status, ErrorCode errorCode,
                        Instant createdAt, Instant finishedAt) {
        static TaskResponse of(AiTask task) {
            return new TaskResponse(task.getId(), task.getType(), task.getApplicationId(), task.getStatus(),
                    task.getErrorCode(), task.getCreatedAt(), task.getFinishedAt());
        }
    }

    record Model(String name, String promptVersion) {
    }

    record AnalysisResponse(List<AnalysisDocument.AnalyzedSkill> skills, List<ResumeExtraction.Experience> experiences,
                            List<ResumeExtraction.Education> educations, List<ResumeExtraction.Language> languages,
                            String summary, Model model, Instant createdAt, String disclaimer) {
    }

    record Breakdown(double requiredCoverage, Double preferredCoverage, double experienceFit, String formulaVersion) {
    }

    record MatchResponse(int score, MatchLabel label, Breakdown breakdown, List<MatchingResult.MatchedSkill> matchedSkills,
                         List<MatchingResult.MissingSkill> missingSkills, List<String> strengths,
                         List<String> weaknesses, String summary, List<String> warnings, String disclaimer,
                         Model model, Instant createdAt) {
    }
}
