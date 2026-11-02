package com.smartrecruit.ai.application;

import com.smartrecruit.ai.domain.AiTask;
import com.smartrecruit.ai.domain.AiTaskStatus;
import com.smartrecruit.ai.domain.AnalysisDocument;
import com.smartrecruit.ai.domain.MatchDocument;
import com.smartrecruit.ai.domain.matching.MatchingResult;
import com.smartrecruit.ai.infrastructure.AiResultRepository;
import com.smartrecruit.ai.infrastructure.AiTaskRepository;
import com.smartrecruit.application.api.ApplicationAiRecorder;
import com.smartrecruit.shared.domain.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.Optional;
import java.util.UUID;

/**
 * Les transactions courtes d'une tâche IA (architecture backend : « trois temps ») : passer RUNNING,
 * puis, après l'appel au modèle fait hors transaction, enregistrer le résultat ou l'échec. Une
 * transaction ouverte pendant deux minutes d'appel bloquerait une connexion du pool.
 */
@Component
public class AiTaskStore {

    private static final Logger log = LoggerFactory.getLogger(AiTaskStore.class);

    private final AiTaskRepository tasks;
    private final AiResultRepository results;
    private final ApplicationAiRecorder recorder;
    private final Clock clock;

    AiTaskStore(AiTaskRepository tasks, AiResultRepository results, ApplicationAiRecorder recorder, Clock clock) {
        this.tasks = tasks;
        this.results = results;
        this.recorder = recorder;
        this.clock = clock;
    }

    /** Temps 1. @return la tâche si elle vient de passer RUNNING ; vide si quelqu'un l'a déjà prise ou close */
    @Transactional
    public Optional<AiTask> start(UUID taskId) {
        return tasks.findById(taskId).filter(task -> task.start(clock.instant())).map(tasks::saveAndFlush);
    }

    /** Analyse enregistrée pendant une tâche (d'analyse ou de matching) ; transaction propre. */
    @Transactional
    public UUID saveAnalysis(AiTask task, String model, String sha256, AnalysisDocument document) {
        return results.saveAnalysis(task.getResumeId(), task.getId(), model, Prompts.RESUME_EXTRACTION, sha256,
                document, clock.instant());
    }

    /** Temps 3 d'une analyse. */
    @Transactional
    public void succeed(UUID taskId) {
        tasks.findById(taskId).ifPresent(task -> task.succeed(clock.instant()));
    }

    /**
     * Temps 3 d'un matching : résultat, copie du score sur la candidature et tâche DONE, ensemble.
     * Aucun statut de candidature n'est modifié (RG-25).
     */
    @Transactional
    public void saveMatch(AiTask task, UUID analysisId, MatchingResult result, MatchDocument document, String model) {
        Instant now = clock.instant();
        results.saveMatch(task.getApplicationId(), analysisId, task.getId(), result, document, model,
                Prompts.MATCH_EXPLANATION, now);
        recorder.matchScored(task.getApplicationId(), result.score(), now);
        tasks.findById(task.getId()).orElseThrow().succeed(now);
    }

    @Transactional
    public void fail(UUID taskId, ErrorCode code) {
        tasks.findById(taskId).ifPresent(task -> task.fail(code, clock.instant()));
    }

    /** Analyse fonctionnelle §4.4 : au démarrage, une tâche PENDING ou RUNNING est orpheline. */
    @Transactional
    public int failOrphans() {
        var orphans = tasks.findByStatusIn(EnumSet.of(AiTaskStatus.PENDING, AiTaskStatus.RUNNING));
        orphans.forEach(task -> task.fail(ErrorCode.AI_UNAVAILABLE, clock.instant()));
        if (!orphans.isEmpty()) {
            log.warn("{} tâche(s) IA orpheline(s) passée(s) FAILED au démarrage", orphans.size());
        }
        return orphans.size();
    }
}
