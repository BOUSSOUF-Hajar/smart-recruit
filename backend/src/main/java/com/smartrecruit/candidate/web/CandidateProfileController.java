package com.smartrecruit.candidate.web;

import com.smartrecruit.candidate.application.CandidateProfileService;
import com.smartrecruit.candidate.web.ProfileRequests.ReplaceLanguagesRequest;
import com.smartrecruit.candidate.web.ProfileRequests.ReplaceLinksRequest;
import com.smartrecruit.candidate.web.ProfileRequests.ReplaceSkillsRequest;
import com.smartrecruit.candidate.web.ProfileRequests.UpdateProfileRequest;
import com.smartrecruit.candidate.web.ProfileResponses.ProfileResponse;
import com.smartrecruit.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Profil du candidat connecté. Toutes les écritures renvoient le profil complet, complétude comprise :
 * l'écran n'a qu'une source de vérité à afficher.
 */
@RestController
@RequestMapping("/api/v1/candidates/me")
class CandidateProfileController {

    private final CandidateProfileService profiles;
    private final CandidateWebMapper mapper;

    CandidateProfileController(CandidateProfileService profiles, CandidateWebMapper mapper) {
        this.profiles = profiles;
        this.mapper = mapper;
    }

    @GetMapping("/profile")
    ProfileResponse get(CurrentUser user) {
        return mapper.toResponse(profiles.get(user));
    }

    /** US-13 */
    @PutMapping("/profile")
    ProfileResponse update(CurrentUser user, @Valid @RequestBody UpdateProfileRequest request) {
        return mapper.toResponse(profiles.updateBasics(user, request.toCommand()));
    }

    /** US-15 */
    @PutMapping("/skills")
    ProfileResponse replaceSkills(CurrentUser user, @Valid @RequestBody ReplaceSkillsRequest request) {
        return mapper.toResponse(profiles.replaceSkills(user, request.toEntries()));
    }

    /** US-16 */
    @PutMapping("/languages")
    ProfileResponse replaceLanguages(CurrentUser user, @Valid @RequestBody ReplaceLanguagesRequest request) {
        return mapper.toResponse(profiles.replaceLanguages(user, request.toEntries()));
    }

    /** US-16 */
    @PutMapping("/links")
    ProfileResponse replaceLinks(CurrentUser user, @Valid @RequestBody ReplaceLinksRequest request) {
        return mapper.toResponse(profiles.replaceLinks(user, request.toEntries()));
    }
}
