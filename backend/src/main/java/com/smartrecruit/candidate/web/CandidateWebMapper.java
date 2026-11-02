package com.smartrecruit.candidate.web;

import com.smartrecruit.candidate.application.ProfileView;
import com.smartrecruit.candidate.application.SkillLine;
import com.smartrecruit.candidate.domain.CandidateLanguage;
import com.smartrecruit.candidate.domain.CandidateLink;
import com.smartrecruit.candidate.domain.Completeness;
import com.smartrecruit.candidate.domain.Education;
import com.smartrecruit.candidate.domain.Experience;
import com.smartrecruit.candidate.domain.Resume;
import com.smartrecruit.candidate.web.ProfileResponses.CompletenessResponse;
import com.smartrecruit.candidate.web.ProfileResponses.EducationResponse;
import com.smartrecruit.candidate.web.ProfileResponses.ExperienceResponse;
import com.smartrecruit.candidate.web.ProfileResponses.LanguageResponse;
import com.smartrecruit.candidate.web.ProfileResponses.LinkResponse;
import com.smartrecruit.candidate.web.ProfileResponses.LocationResponse;
import com.smartrecruit.candidate.web.ProfileResponses.ProfileResponse;
import com.smartrecruit.candidate.web.ProfileResponses.ResumeResponse;
import com.smartrecruit.candidate.web.ProfileResponses.SkillResponse;
import org.mapstruct.Mapper;

import java.util.List;

/**
 * Entités et vues → DTO de réponse, code généré par MapStruct à la compilation. Une propriété de
 * réponse sans source fait échouer la compilation ({@code unmappedTargetPolicy=ERROR} dans le pom).
 */
@Mapper
interface CandidateWebMapper {

    ExperienceResponse toResponse(Experience experience);

    EducationResponse toResponse(Education education);

    SkillResponse toResponse(SkillLine skill);

    LanguageResponse toResponse(CandidateLanguage language);

    LinkResponse toResponse(CandidateLink link);

    ResumeResponse toResponse(Resume resume);

    CompletenessResponse toResponse(Completeness completeness);

    List<ExperienceResponse> experiences(List<Experience> experiences);

    List<EducationResponse> educations(List<Education> educations);

    List<SkillResponse> skills(List<SkillLine> skills);

    List<LanguageResponse> languages(List<CandidateLanguage> languages);

    List<LinkResponse> links(List<CandidateLink> links);

    /** Assemblage écrit à la main : il combine le compte, le profil et le CV en une seule réponse. */
    default ProfileResponse toResponse(ProfileView view) {
        ProfileView.Basics basics = view.basics();
        LocationResponse location = basics.city() == null && basics.countryCode() == null
                ? null : new LocationResponse(basics.city(), basics.countryCode());
        return new ProfileResponse(view.account().firstName(), view.account().lastName(), view.account().email(),
                basics.phone(), location, basics.headline(), basics.summary(), basics.yearsOfExperience(),
                experiences(view.experiences()), educations(view.educations()), skills(view.skills()),
                languages(view.languages()), links(view.links()),
                view.resume() == null ? null : toResponse(view.resume()),
                toResponse(view.completeness()), view.version());
    }
}
