package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.application.ProfileCommands.LanguageEntry;
import com.smartrecruit.candidate.application.ProfileCommands.LinkEntry;
import com.smartrecruit.candidate.application.ProfileCommands.SkillEntry;
import com.smartrecruit.candidate.application.ProfileCommands.UpdateBasics;
import com.smartrecruit.candidate.domain.CandidateLanguage;
import com.smartrecruit.candidate.domain.CandidateLink;
import com.smartrecruit.candidate.domain.CandidateProfile;
import com.smartrecruit.candidate.domain.CandidateSkill;
import com.smartrecruit.candidate.domain.Completeness;
import com.smartrecruit.candidate.domain.Education;
import com.smartrecruit.candidate.domain.Experience;
import com.smartrecruit.candidate.domain.Resume;
import com.smartrecruit.candidate.infrastructure.CandidateProfileRepository;
import com.smartrecruit.candidate.infrastructure.EducationRepository;
import com.smartrecruit.candidate.infrastructure.ExperienceRepository;
import com.smartrecruit.candidate.infrastructure.ResumeRepository;
import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.referential.api.SkillCatalog;
import com.smartrecruit.referential.api.SkillSummary;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.FieldValidationException.FieldError;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Profil du candidat connecté (US-13, US-15, US-16, US-20). L'identifiant vient toujours de
 * {@link CurrentUser}, jamais de l'URL : le profil d'un autre candidat n'est pas atteignable (RG-06).
 */
@Service
public class CandidateProfileService {

    private final CandidateProfileRepository profiles;
    private final ExperienceRepository experiences;
    private final EducationRepository educations;
    private final ResumeRepository resumes;
    private final UserAccounts accounts;
    private final SkillCatalog skillCatalog;

    public CandidateProfileService(CandidateProfileRepository profiles, ExperienceRepository experiences,
                                   EducationRepository educations, ResumeRepository resumes,
                                   UserAccounts accounts, SkillCatalog skillCatalog) {
        this.profiles = profiles;
        this.experiences = experiences;
        this.educations = educations;
        this.resumes = resumes;
        this.accounts = accounts;
        this.skillCatalog = skillCatalog;
    }

    @Transactional(readOnly = true)
    public ProfileView get(CurrentUser user) {
        return view(load(user));
    }

    /**
     * US-13. Prénom et nom sont enregistrés sur le compte, le reste sur le profil, dans la même
     * transaction. La version envoyée doit être celle lue : sinon 409, rien n'est écrasé.
     */
    @Transactional
    public ProfileView updateBasics(CurrentUser user, UpdateBasics command) {
        CandidateProfile profile = load(user);
        if (profile.getVersion() == null || profile.getVersion() != command.version()) {
            throw new OptimisticLockingFailureException("Profil " + user.id() + " : version " + command.version()
                    + " obsolète (actuelle " + profile.getVersion() + ")");
        }
        accounts.rename(user.id(), command.firstName(), command.lastName());
        profile.updateBasics(command.phone(), command.city(), command.countryCode(), command.headline(),
                command.summary(), command.yearsOfExperience());
        profiles.flush();   // la réponse porte la nouvelle version
        return view(profile);
    }

    /** US-15. Chaque compétence doit exister et être active dans le référentiel. */
    @Transactional
    public ProfileView replaceSkills(CurrentUser user, List<SkillEntry> entries) {
        CandidateProfile profile = load(user);
        Map<UUID, SkillSummary> known = skillCatalog.findByIds(entries.stream().map(SkillEntry::skillId).toList());
        List<FieldError> errors = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            SkillSummary skill = known.get(entries.get(i).skillId());
            if (skill == null || !skill.active()) {
                errors.add(new FieldError("skills[" + i + "].skillId", "Compétence inconnue ou désactivée."));
            }
        }
        if (!errors.isEmpty()) {
            throw new FieldValidationException(errors);
        }
        profile.replaceSkills(entries.stream()
                .map(entry -> new CandidateSkill(entry.skillId(), entry.level(), entry.years()))
                .toList());
        return view(profile);
    }

    /** US-16 */
    @Transactional
    public ProfileView replaceLanguages(CurrentUser user, List<LanguageEntry> entries) {
        CandidateProfile profile = load(user);
        profile.replaceLanguages(entries.stream()
                .map(entry -> new CandidateLanguage(entry.languageCode(), entry.level()))
                .toList());
        return view(profile);
    }

    /** US-16 */
    @Transactional
    public ProfileView replaceLinks(CurrentUser user, List<LinkEntry> entries) {
        CandidateProfile profile = load(user);
        profile.replaceLinks(entries.stream().map(entry -> new CandidateLink(entry.type(), entry.url())).toList());
        return view(profile);
    }

    /** Profil d'un candidat quelconque, pour l'API du module (dossier de candidature). */
    @Transactional(readOnly = true)
    ProfileView viewOf(UUID candidateId) {
        return view(profiles.findById(candidateId).orElseThrow(() -> new ResourceNotFoundException("Candidat")));
    }

    private CandidateProfile load(CurrentUser user) {
        return profiles.findById(user.id()).orElseThrow(() -> new ResourceNotFoundException("Profil"));
    }

    private ProfileView view(CandidateProfile profile) {
        UUID candidateId = profile.getUserId();
        AccountSummary account = accounts.get(candidateId);
        List<Experience> sortedExperiences = experiences.findByCandidateId(candidateId).stream()
                .sorted(Experience.MOST_RECENT_FIRST).toList();
        List<Education> sortedEducations = educations.findByCandidateId(candidateId).stream()
                .sorted(Education.MOST_RECENT_FIRST).toList();
        List<SkillLine> skills = skillLines(profile.getSkills());
        // Tri explicite : juste après un remplacement, la liste en mémoire garde l'ordre d'envoi
        List<CandidateLanguage> languages = profile.getLanguages().stream()
                .sorted(Comparator.comparing(CandidateLanguage::getLanguageCode)).toList();
        List<CandidateLink> links = profile.getLinks().stream()
                .sorted(Comparator.comparing(CandidateLink::getType).thenComparing(CandidateLink::getUrl)).toList();
        Resume resume = resumes.findByCandidateIdAndActiveTrue(candidateId).orElse(null);

        Completeness completeness = Completeness.of(new Completeness.Facts(account.firstName(), account.lastName(),
                profile.getHeadline(), profile.getSummary(), sortedExperiences.size(), sortedEducations.size(),
                skills.size(), languages.size(), resume != null));

        return new ProfileView(account,
                new ProfileView.Basics(profile.getPhone(), profile.getCity(), profile.getCountryCode(),
                        profile.getHeadline(), profile.getSummary(), profile.getYearsOfExperience()),
                sortedExperiences, sortedEducations, skills, languages, links, resume, completeness,
                profile.getVersion() == null ? 0 : profile.getVersion());
    }

    /** Noms lus dans le référentiel ; une compétence désactivée depuis reste affichée. */
    private List<SkillLine> skillLines(List<CandidateSkill> skills) {
        Map<UUID, SkillSummary> names = skillCatalog.findByIds(skills.stream().map(CandidateSkill::getSkillId).toList());
        return skills.stream()
                .map(skill -> new SkillLine(skill.getSkillId(),
                        names.containsKey(skill.getSkillId()) ? names.get(skill.getSkillId()).name() : "?",
                        skill.getLevel(), skill.getYears()))
                .sorted(Comparator.comparing(SkillLine::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }
}
