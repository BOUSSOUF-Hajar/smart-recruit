package com.smartrecruit.candidate.web;

import com.smartrecruit.candidate.application.CandidateSectionsService;
import com.smartrecruit.candidate.domain.Education;
import com.smartrecruit.candidate.domain.Experience;
import com.smartrecruit.candidate.web.ProfileRequests.EducationRequest;
import com.smartrecruit.candidate.web.ProfileRequests.ExperienceRequest;
import com.smartrecruit.candidate.web.ProfileResponses.EducationResponse;
import com.smartrecruit.candidate.web.ProfileResponses.ExperienceResponse;
import com.smartrecruit.shared.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.net.URI;
import java.util.UUID;

/** US-14 : expériences et formations, éditées une par une. */
@RestController
@RequestMapping("/api/v1/candidates/me")
class CandidateSectionsController {

    private final CandidateSectionsService sections;
    private final CandidateWebMapper mapper;

    CandidateSectionsController(CandidateSectionsService sections, CandidateWebMapper mapper) {
        this.sections = sections;
        this.mapper = mapper;
    }

    @PostMapping("/experiences")
    ResponseEntity<ExperienceResponse> addExperience(CurrentUser user, @Valid @RequestBody ExperienceRequest request) {
        Experience created = sections.addExperience(user, request.toDetails());
        return ResponseEntity.created(URI.create("/api/v1/candidates/me/experiences/" + created.getId()))
                .body(mapper.toResponse(created));
    }

    @PutMapping("/experiences/{id}")
    ExperienceResponse updateExperience(CurrentUser user, @PathVariable UUID id,
                                        @Valid @RequestBody ExperienceRequest request) {
        return mapper.toResponse(sections.updateExperience(user, id, request.toDetails()));
    }

    @DeleteMapping("/experiences/{id}")
    ResponseEntity<Void> deleteExperience(CurrentUser user, @PathVariable UUID id) {
        sections.deleteExperience(user, id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/educations")
    ResponseEntity<EducationResponse> addEducation(CurrentUser user, @Valid @RequestBody EducationRequest request) {
        Education created = sections.addEducation(user, request.toDetails());
        return ResponseEntity.created(URI.create("/api/v1/candidates/me/educations/" + created.getId()))
                .body(mapper.toResponse(created));
    }

    @PutMapping("/educations/{id}")
    EducationResponse updateEducation(CurrentUser user, @PathVariable UUID id,
                                      @Valid @RequestBody EducationRequest request) {
        return mapper.toResponse(sections.updateEducation(user, id, request.toDetails()));
    }

    @DeleteMapping("/educations/{id}")
    ResponseEntity<Void> deleteEducation(CurrentUser user, @PathVariable UUID id) {
        sections.deleteEducation(user, id);
        return ResponseEntity.noContent().build();
    }
}
