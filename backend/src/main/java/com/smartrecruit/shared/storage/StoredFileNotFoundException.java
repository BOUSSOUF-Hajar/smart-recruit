package com.smartrecruit.shared.storage;

/**
 * Fichier référencé en base mais absent du stockage : incohérence à signaler dans les logs,
 * présentée à l'utilisateur comme une ressource introuvable.
 */
public class StoredFileNotFoundException extends RuntimeException {

    public StoredFileNotFoundException(String key) {
        super("Fichier absent du stockage (clé " + key + ")");
    }
}
