package com.smartrecruit.ai.application;

import com.smartrecruit.shared.domain.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Soumet une tâche à l'exécuteur IA <b>après</b> le commit qui l'a créée : avant, le thread d'arrière-
 * plan pourrait chercher une tâche pas encore visible en base (analyse fonctionnelle §3.5). File
 * pleine → la tâche passe FAILED avec {@code AI_UNAVAILABLE}.
 */
@Component
class AiTaskSubmitter {

    private static final Logger log = LoggerFactory.getLogger(AiTaskSubmitter.class);

    private final TaskExecutor executor;
    private final AiTaskWorker worker;
    private final AiTaskStore store;

    AiTaskSubmitter(@Qualifier("aiExecutor") TaskExecutor executor, AiTaskWorker worker, AiTaskStore store) {
        this.executor = executor;
        this.worker = worker;
        this.store = store;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void submit(AiTaskCreated event) {
        try {
            executor.execute(() -> worker.run(event.taskId()));
        } catch (TaskRejectedException e) {
            log.warn("File IA pleine : tâche {} refusée", event.taskId());
            store.fail(event.taskId(), ErrorCode.AI_UNAVAILABLE);
        }
    }

    /** Analyse fonctionnelle §4.4 : rattrapage des tâches orphelines après un redémarrage. */
    @EventListener(ApplicationReadyEvent.class)
    void recoverOrphans() {
        store.failOrphans();
    }
}
