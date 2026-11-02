package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.api.ApplicationReadiness;
import com.smartrecruit.candidate.api.CandidateDirectory;
import com.smartrecruit.candidate.api.CandidateDossier;
import com.smartrecruit.candidate.api.ProfileOverview;
import com.smartrecruit.candidate.api.ResumeContent;
import com.smartrecruit.candidate.api.ResumeSummary;
import com.smartrecruit.candidate.domain.CandidateProfile;
import com.smartrecruit.candidate.domain.Resume;
import com.smartrecruit.candidate.infrastructure.CandidateProfileRepository;
import com.smartrecruit.candidate.infrastructure.ResumeRepository;
import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.storage.FileStorage;
import com.smartrecruit.shared.storage.StoredFileNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Implémentation de l'API du module pour les candidatures (US-27, US-31). */
@Service
@Transactional(readOnly = true)
class CandidateDirectoryService implements CandidateDirectory {

    private static final Logger log = LoggerFactory.getLogger(CandidateDirectoryService.class);

    private final CandidateProfileRepository profiles;
    private final ResumeRepository resumes;
    private final CandidateProfileService profileService;
    private final UserAccounts accounts;
    private final FileStorage storage;

    CandidateDirectoryService(CandidateProfileRepository profiles, ResumeRepository resumes,
                              CandidateProfileService profileService, UserAccounts accounts, FileStorage storage) {
        this.profiles = profiles;
        this.resumes = resumes;
        this.profileService = profileService;
        this.accounts = accounts;
        this.storage = storage;
    }

    @Override
    public ApplicationReadiness readiness(UUID candidateId) {
        CandidateProfile profile = profiles.findById(candidateId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil"));
        AccountSummary account = accounts.get(candidateId);
        Optional<Resume> resume = resumes.findByCandidateIdAndActiveTrue(candidateId);
        List<String> missing = new ArrayList<>();
        if (blank(account.firstName()) || blank(account.lastName())) {
            missing.add("name");
        }
        if (blank(profile.getHeadline())) {
            missing.add("headline");
        }
        if (profile.getSkills().isEmpty()) {
            missing.add("skills");
        }
        if (resume.isEmpty()) {
            missing.add("resume");
        }
        return ApplicationReadiness.of(missing, resume.map(Resume::getId).orElse(null));
    }

    @Override
    public CandidateDossier dossier(UUID candidateId) {
        ProfileView view = profileService.viewOf(candidateId);
        ProfileView.Basics basics = view.basics();
        return new CandidateDossier(candidateId, view.account().email(), view.account().firstName(),
                view.account().lastName(), basics.phone(), basics.city(), basics.countryCode(), basics.headline(),
                basics.summary(), basics.yearsOfExperience(),
                view.experiences().stream().map(e -> new CandidateDossier.ExperienceItem(e.getJobTitle(),
                        e.getCompanyName(), e.getCity(), e.getStartDate(), e.getEndDate(), e.getDescription())).toList(),
                view.educations().stream().map(e -> new CandidateDossier.EducationItem(e.getDegree(), e.getSchool(),
                        e.getFieldOfStudy(), e.getStartYear(), e.getEndYear())).toList(),
                view.skills().stream().map(s -> new CandidateDossier.SkillItem(s.skillId(), s.name(), s.level(),
                        s.years())).toList(),
                view.languages().stream().map(l -> new CandidateDossier.LanguageItem(l.getLanguageCode(),
                        l.getLevel().name())).toList(),
                view.links().stream().map(l -> new CandidateDossier.LinkItem(l.getType().name(), l.getUrl())).toList());
    }

    @Override
    public ProfileOverview overview(UUID candidateId) {
        ProfileView view = profileService.viewOf(candidateId);
        return new ProfileOverview(view.completeness().percent(), view.completeness().missing(),
                view.resume() != null, view.resume() == null ? null : view.resume().getUploadedAt());
    }

    @Override
    public Optional<ResumeSummary> resume(UUID resumeId) {
        return resumes.findById(resumeId).map(CandidateDirectoryService::summary);
    }

    @Override
    public ResumeContent resumeContent(UUID resumeId) {
        Resume resume = resumes.findById(resumeId).orElseThrow(() -> new ResourceNotFoundException("CV"));
        try {
            return new ResumeContent(storage.load(resume.getStorageKey()), resume.getOriginalFilename());
        } catch (StoredFileNotFoundException e) {
            log.error("Fichier du CV {} absent du stockage (candidat {})", resume.getId(), resume.getCandidateId());
            throw new ResourceNotFoundException("CV");
        }
    }

    static ResumeSummary summary(Resume resume) {
        return new ResumeSummary(resume.getId(), resume.getCandidateId(), resume.getOriginalFilename(),
                resume.getSizeBytes(), resume.getUploadedAt(), resume.isActive());
    }

    private static boolean blank(String value) {
        return value == null || value.isBlank();
    }
}
