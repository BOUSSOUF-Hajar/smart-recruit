package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.domain.Resume;
import com.smartrecruit.candidate.infrastructure.CandidateProfileRepository;
import com.smartrecruit.candidate.infrastructure.ResumeRepository;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * Partie base de données du téléversement, dans une transaction courte qui ne contient aucune
 * écriture de fichier. Classe distincte de {@link ResumeService} pour que la transaction s'applique.
 */
@Component
class ResumeRegistry {

    private final ResumeRepository resumes;
    private final CandidateProfileRepository profiles;
    private final Clock clock;

    ResumeRegistry(ResumeRepository resumes, CandidateProfileRepository profiles, Clock clock) {
        this.resumes = resumes;
        this.profiles = profiles;
        this.clock = clock;
    }

    /** Désactive le CV actif puis enregistre le nouveau (RG-08), dans cet ordre, à cause de l'index unique partiel. */
    @Transactional
    public Resume activate(UUID candidateId, String storageKey, String originalFilename, int sizeBytes,
                           String sha256) {
        if (!profiles.existsById(candidateId)) {
            throw new ResourceNotFoundException("Profil");
        }
        Instant now = clock.instant();
        resumes.findByCandidateIdAndActiveTrue(candidateId).ifPresent(previous -> {
            previous.deactivate(now);
            resumes.saveAndFlush(previous);
        });
        return resumes.saveAndFlush(Resume.activeVersion(candidateId, storageKey, originalFilename, sizeBytes,
                sha256, now));
    }

    /**
     * US-19, partie base. Un CV joint à une candidature est seulement désactivé (RG-12) ; sinon la ligne
     * est supprimée et la clé du fichier renvoyée, pour l'effacer une fois la transaction validée.
     * <p>
     * Une candidature créée au même instant avec ce CV ferait échouer la suppression sur la clé
     * étrangère {@code fk_application_resume} : rien n'est perdu, le candidat réessaie.
     */
    @Transactional
    public Optional<String> remove(UUID candidateId, Predicate<UUID> attached) {
        Resume resume = resumes.findByCandidateIdAndActiveTrue(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("CV"));
        if (attached.test(resume.getId())) {
            resume.deactivate(clock.instant());
            return Optional.empty();
        }
        resumes.delete(resume);
        resumes.flush();
        return Optional.of(resume.getStorageKey());
    }

    @Transactional(readOnly = true)
    public Optional<Resume> active(UUID candidateId) {
        return resumes.findByCandidateIdAndActiveTrue(candidateId);
    }
}
