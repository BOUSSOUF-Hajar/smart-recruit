package com.smartrecruit.candidate.web;

import com.smartrecruit.candidate.application.ResumeService;
import com.smartrecruit.candidate.application.UploadedFile;
import com.smartrecruit.candidate.domain.Resume;
import com.smartrecruit.candidate.domain.ResumeRules;
import com.smartrecruit.candidate.web.ProfileResponses.ResumeResponse;
import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

/** US-17, US-18 : CV actif du candidat connecté. Jamais d'URL publique vers le fichier (RG-07). */
@RestController
@RequestMapping("/api/v1/candidates/me/resume")
class ResumeController {

    private final ResumeService resumes;
    private final CandidateWebMapper mapper;

    ResumeController(ResumeService resumes, CandidateWebMapper mapper) {
        this.resumes = resumes;
        this.mapper = mapper;
    }

    @GetMapping
    ResumeResponse active(CurrentUser user) {
        return mapper.toResponse(resumes.active(user));
    }

    /** Champ multipart unique {@code file}. Remplace le CV actif ; l'ancien reste lié aux candidatures envoyées. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<ResumeResponse> upload(CurrentUser user, @RequestParam("file") MultipartFile file)
            throws IOException {
        // Refus avant de charger le contenu en mémoire (la limite multipart l'a déjà borné à 5 Mo)
        if (file.getSize() > ResumeRules.MAX_SIZE_BYTES) {
            throw new BusinessRuleViolationException(ErrorCode.FILE_TOO_LARGE,
                    "Le fichier dépasse la taille maximale de 5 Mo.");
        }
        Resume resume = resumes.upload(user,
                new UploadedFile(file.getOriginalFilename(), file.getContentType(), file.getBytes()));
        return ResponseEntity.created(URI.create("/api/v1/candidates/me/resume")).body(mapper.toResponse(resume));
    }

    /** US-19 : effacé, ou seulement désactivé s'il est joint à une candidature (RG-12). */
    @DeleteMapping
    ResponseEntity<Void> delete(CurrentUser user) {
        resumes.delete(user);
        return ResponseEntity.noContent().build();
    }

    /** Téléchargement forcé ({@code attachment}) : le PDF n'est jamais affiché dans le contexte de l'application. */
    @GetMapping("/file")
    ResponseEntity<Resource> download(CurrentUser user) {
        ResumeService.ResumeDownload download = resumes.download(user);
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(download.filename(), StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .cacheControl(CacheControl.noStore())
                .body(download.file());
    }
}
