package com.smartrecruit.candidate.application;

/**
 * Fichier reçu, indépendant de l'API multipart de Spring.
 *
 * @param originalFilename nom envoyé par le navigateur, non fiable
 * @param contentType      type déclaré par le navigateur, non fiable (déduit de l'extension)
 */
public record UploadedFile(String originalFilename, String contentType, byte[] content) {
}
