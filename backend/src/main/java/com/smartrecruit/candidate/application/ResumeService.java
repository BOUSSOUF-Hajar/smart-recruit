package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.domain.OriginalFilename;
import com.smartrecruit.candidate.domain.Resume;
import com.smartrecruit.candidate.domain.ResumeRules;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.storage.FileStorage;
import com.smartrecruit.shared.storage.StoredFileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.smartrecruit.candidate.api.ResumeUsage;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * CV du candidat connecté (US-17, US-18).
 * <p>
 * Le disque et la base ne partagent pas de transaction (analyse fonctionnelle §3.2) : le fichier
 * est écrit d'abord, puis la base ; si la base échoue, le fichier est supprimé (compensation).
 * Volontairement sans {@code @Transactional} ici : aucune écriture de fichier dans une transaction.
 */
@Service
public class ResumeService {

    private static final Logger log = LoggerFactory.getLogger(ResumeService.class);

    private final ResumeInspector inspector;
    private final ResumeRegistry registry;
    private final FileStorage storage;
    private final ObjectProvider<ResumeUsage> usage;

    public ResumeService(ResumeInspector inspector, ResumeRegistry registry, FileStorage storage,
                         ObjectProvider<ResumeUsage> usage) {
        this.inspector = inspector;
        this.registry = registry;
        this.storage = storage;
        this.usage = usage;
    }

    public Resume upload(CurrentUser user, UploadedFile file) {
        inspector.inspect(file);
        String key = storage.store(file.content(), ResumeRules.EXTENSION);
        try {
            return registry.activate(user.id(), key, OriginalFilename.sanitize(file.originalFilename()),
                    file.content().length, sha256(file.content()));
        } catch (RuntimeException e) {
            deleteQuietly(key);
            throw e;
        }
    }

    public Resume active(CurrentUser user) {
        return registry.active(user.id()).orElseThrow(() -> new ResourceNotFoundException("CV"));
    }

    /**
     * US-18. Un fichier absent du disque est une incohérence : alerte dans les logs (sans chemin),
     * simple 404 pour l'utilisateur.
     */
    public ResumeDownload download(CurrentUser user) {
        Resume resume = active(user);
        try {
            return new ResumeDownload(storage.load(resume.getStorageKey()), resume.getOriginalFilename());
        } catch (StoredFileNotFoundException e) {
            log.error("Fichier du CV {} absent du stockage (candidat {})", resume.getId(), user.id());
            throw new ResourceNotFoundException("CV");
        }
    }

    /**
     * US-19. La base d'abord (transaction courte), le fichier ensuite : si l'effacement du fichier
     * échoue, il ne reste qu'un fichier orphelin, jamais une ligne qui pointe vers un fichier absent.
     */
    public void delete(CurrentUser user) {
        ResumeUsage applications = usage.getIfAvailable();
        registry.remove(user.id(), resumeId -> applications != null && applications.isAttached(resumeId))
                .ifPresent(this::deleteQuietly);
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            // Fichier orphelin : le nettoyage périodique prévu en V2 le retirera
            log.warn("Compensation impossible : fichier {} non supprimé", key, e);
        }
    }

    static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 est garanti par la plateforme Java", e);
        }
    }

    /** Fichier à servir et nom proposé au téléchargement. */
    public record ResumeDownload(Resource file, String filename) {
    }
}
