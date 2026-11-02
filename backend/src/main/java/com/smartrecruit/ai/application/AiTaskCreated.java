package com.smartrecruit.ai.application;

import java.util.UUID;

/** Interne au module : une tâche vient d'être créée ; elle sera soumise à l'exécuteur après le commit. */
record AiTaskCreated(UUID taskId) {
}
