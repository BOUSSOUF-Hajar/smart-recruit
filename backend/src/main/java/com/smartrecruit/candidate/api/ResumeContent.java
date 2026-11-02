package com.smartrecruit.candidate.api;

import org.springframework.core.io.Resource;

/** Fichier à servir et nom proposé au téléchargement. */
public record ResumeContent(Resource file, String filename) {
}
