package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.domain.Education;
import com.smartrecruit.candidate.domain.EducationDetails;
import com.smartrecruit.candidate.domain.Experience;
import com.smartrecruit.candidate.domain.ExperienceDetails;
import com.smartrecruit.candidate.infrastructure.CandidateProfileRepository;
import com.smartrecruit.candidate.infrastructure.EducationRepository;
import com.smartrecruit.candidate.infrastructure.ExperienceRepository;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Expériences et formations (US-14), éditées une par une. Chaque lecture filtre sur le candidat
 * connecté : l'élément d'un autre candidat répond 404, exactement comme un élément inexistant.
 */
@Service
public class CandidateSectionsService {

    private final CandidateProfileRepository profiles;
    private final ExperienceRepository experiences;
    private final EducationRepository educations;

    public CandidateSectionsService(CandidateProfileRepository profiles, ExperienceRepository experiences,
                                    EducationRepository educations) {
        this.profiles = profiles;
        this.experiences = experiences;
        this.educations = educations;
    }

    @Transactional
    public Experience addExperience(CurrentUser user, ExperienceDetails details) {
        requireProfile(user);
        return experiences.save(Experience.create(user.id(), details));
    }

    @Transactional
    public Experience updateExperience(CurrentUser user, UUID id, ExperienceDetails details) {
        Experience experience = experience(user, id);
        experience.update(details);
        return experience;
    }

    @Transactional
    public void deleteExperience(CurrentUser user, UUID id) {
        experiences.delete(experience(user, id));
    }

    @Transactional
    public Education addEducation(CurrentUser user, EducationDetails details) {
        requireProfile(user);
        return educations.save(Education.create(user.id(), details));
    }

    @Transactional
    public Education updateEducation(CurrentUser user, UUID id, EducationDetails details) {
        Education education = education(user, id);
        education.update(details);
        return education;
    }

    @Transactional
    public void deleteEducation(CurrentUser user, UUID id) {
        educations.delete(education(user, id));
    }

    private Experience experience(CurrentUser user, UUID id) {
        return experiences.findByIdAndCandidateId(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Expérience"));
    }

    private Education education(CurrentUser user, UUID id) {
        return educations.findByIdAndCandidateId(id, user.id())
                .orElseThrow(() -> new ResourceNotFoundException("Formation"));
    }

    private void requireProfile(CurrentUser user) {
        if (!profiles.existsById(user.id())) {
            throw new ResourceNotFoundException("Profil");
        }
    }
}
