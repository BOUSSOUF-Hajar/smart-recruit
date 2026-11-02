package com.smartrecruit.shared.storage;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Stockage sur le disque local (un volume Docker en production).
 * <p>
 * Défense contre la traversée de répertoires à deux niveaux : la clé est générée ici (UUID) et
 * vérifiée par une expression stricte avant tout accès, puis le chemin résolu doit rester sous la racine.
 */
@Component
public class LocalFileStorage implements FileStorage {

    private static final Pattern KEY = Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.[a-z0-9]{1,8}");
    private static final Pattern EXTENSION = Pattern.compile("[a-z0-9]{1,8}");

    private final Path root;

    public LocalFileStorage(StorageProperties properties) {
        this.root = properties.rootDirectory().toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de créer le dossier de stockage " + root, e);
        }
    }

    @Override
    public String store(byte[] content, String extension) {
        if (!EXTENSION.matcher(extension).matches()) {
            throw new IllegalArgumentException("Extension refusée : " + extension);
        }
        String key = UUID.randomUUID() + "." + extension;
        Path target = resolve(key);
        try {
            // Écriture dans un fichier temporaire du même dossier, puis renommage atomique :
            // un lecteur ne voit jamais un fichier à moitié écrit.
            Path temporary = Files.createTempFile(root, "upload-", ".part");
            try {
                Files.write(temporary, content);
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE);
            } finally {
                Files.deleteIfExists(temporary);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Écriture du fichier impossible", e);
        }
        return key;
    }

    @Override
    public Resource load(String key) {
        Path file = resolve(key);
        if (!Files.isRegularFile(file)) {
            throw new StoredFileNotFoundException(key);
        }
        return new FileSystemResource(file);
    }

    @Override
    public void delete(String key) {
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            throw new UncheckedIOException("Suppression du fichier impossible", e);
        }
    }

    private Path resolve(String key) {
        if (key == null || !KEY.matcher(key).matches()) {
            throw new IllegalArgumentException("Clé de stockage invalide");
        }
        Path file = root.resolve(key).normalize();
        if (!file.startsWith(root)) {
            throw new IllegalArgumentException("Clé de stockage hors du dossier racine");
        }
        return file;
    }
}
