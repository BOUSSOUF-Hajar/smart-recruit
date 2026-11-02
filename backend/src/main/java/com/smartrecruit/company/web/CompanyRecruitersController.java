package com.smartrecruit.company.web;

import com.smartrecruit.company.api.CompanyDirectory;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Recruteurs de l'entreprise du recruteur connecté : choix des participants d'un entretien (US-35). */
@RestController
class CompanyRecruitersController {

    private final CompanyDirectory companies;

    CompanyRecruitersController(CompanyDirectory companies) {
        this.companies = companies;
    }

    @GetMapping("/api/v1/company/recruiters")
    List<RecruiterResponse> recruiters(CurrentUser recruiter) {
        if (recruiter.companyId() == null) {
            throw new ResourceNotFoundException("Entreprise");
        }
        return companies.recruitersOf(recruiter.companyId()).stream()
                .map(r -> new RecruiterResponse(r.userId(), r.fullName(), r.jobTitle()))
                .toList();
    }

    record RecruiterResponse(UUID id, String name, String jobTitle) {
    }
}
