package com.smartrecruit.company.application;

import com.smartrecruit.company.domain.Company;
import com.smartrecruit.company.domain.RecruiterProfile;
import com.smartrecruit.company.infrastructure.CompanyRepository;
import com.smartrecruit.company.infrastructure.RecruiterProfileRepository;
import com.smartrecruit.identity.api.StaffAccountCreated;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.security.Role;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * US-47 : rattache un nouveau recruteur à son entreprise, dans la transaction de création du compte.
 * Une entreprise inconnue ou inactive fait échouer toute la création.
 */
@Component
class StaffAccountListener {

    private final CompanyRepository companies;
    private final RecruiterProfileRepository recruiters;

    StaffAccountListener(CompanyRepository companies, RecruiterProfileRepository recruiters) {
        this.companies = companies;
        this.recruiters = recruiters;
    }

    @EventListener
    @Transactional(propagation = Propagation.MANDATORY)
    public void on(StaffAccountCreated event) {
        if (event.role() != Role.RECRUITER) {
            return;
        }
        Company company = companies.findById(event.companyId())
                .orElseThrow(() -> new FieldValidationException("companyId", "Entreprise inconnue."));
        if (!company.isActive()) {
            throw new InvalidStateException(ErrorCode.INVALID_STATE,
                    "Cette entreprise est désactivée : aucun recruteur ne peut y être ajouté.");
        }
        recruiters.save(RecruiterProfile.create(event.userId(), company.getId(), event.jobTitle()));
    }
}
