package com.smartrecruit.shared.storage;

import org.springframework.core.io.Resource;

/**
 * Stockage des fichiers téléversés (CV). Les appelants ne manipulent qu'une clé opaque :
 * jamais un chemin, jamais le nom d'origine du fichier.
 * <p>
 * Le stockage n'est pas transactionnel : l'appelant écrit le fichier avant la base, et le supprime
 * si l'enregistrement en base échoue (compensation, analyse fonctionnelle §3.2).
 */
public interface FileStorage {

    /**
     * @param extension extension sans point, choisie par l'application ({@code pdf}), jamais par le client
     * @return clé générée, à enregistrer en base
     */
    String store(byte[] content, String extension);

    /** @throws StoredFileNotFoundException si le fichier n'existe plus */
    Resource load(String key);

    /** Sans effet si le fichier n'existe pas. */
    void delete(String key);
}
