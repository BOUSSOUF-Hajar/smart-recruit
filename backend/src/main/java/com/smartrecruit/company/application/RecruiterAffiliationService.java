package com.smartrecruit.company.application;

import com.smartrecruit.company.domain.Company;
import com.smartrecruit.company.domain.RecruiterProfile;
import com.smartrecruit.company.infrastructure.CompanyRepository;
import com.smartrecruit.company.infrastructure.RecruiterProfileRepository;
import com.smartrecruit.identity.api.RecruiterAffiliations;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Profils recruteurs vus par l'administration des comptes (US-48) : deux requêtes par lecture. */
@Service
class RecruiterAffiliationService implements RecruiterAffiliations {

    private final RecruiterProfileRepository recruiters;
    private final CompanyRepository companies;

    RecruiterAffiliationService(RecruiterProfileRepository recruiters, CompanyRepository companies) {
        this.recruiters = recruiters;
        this.companies = companies;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, Affiliation> of(Collection<UUID> recruiterIds) {
        List<RecruiterProfile> profiles = recruiters.findAllById(recruiterIds);
        Map<UUID, Company> byId = companies.findAllById(profiles.stream().map(RecruiterProfile::getCompanyId)
                .distinct().toList()).stream().collect(Collectors.toMap(Company::getId, Function.identity()));
        Map<UUID, Affiliation> result = new HashMap<>();
        profiles.forEach(profile -> {
            Company company = byId.get(profile.getCompanyId());
            result.put(profile.getUserId(), new Affiliation(company.getId(), company.getName(), company.isActive(),
                    profile.getJobTitle()));
        });
        return result;
    }

    @Override
    @Transactional(readOnly = true)
    public Set<UUID> recruitersOf(UUID companyId) {
        return recruiters.findByCompanyId(companyId).stream().map(RecruiterProfile::getUserId)
                .collect(Collectors.toSet());
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean update(UUID recruiterId, UUID companyId, String jobTitle) {
        RecruiterProfile profile = recruiters.findById(recruiterId)
                .orElseThrow(() -> new ResourceNotFoundException("Profil recruteur"));
        if (!profile.getCompanyId().equals(companyId)) {
            Company target = companies.findById(companyId)
                    .orElseThrow(() -> new FieldValidationException("companyId", "Entreprise inconnue."));
            if (!target.isActive()) {
                throw new InvalidStateException(ErrorCode.INVALID_STATE,
                        "Cette entreprise est désactivée : aucun recruteur ne peut y être rattaché.");
            }
        }
        return profile.reassign(companyId, jobTitle);
    }
}
