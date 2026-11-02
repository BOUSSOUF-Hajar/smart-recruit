package com.smartrecruit.company.application;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.company.api.CompanyDirectory;
import com.smartrecruit.company.api.CompanyOffers;
import com.smartrecruit.company.api.CompanyOffers.OfferCounts;
import com.smartrecruit.company.api.CompanySummary;
import com.smartrecruit.company.api.RecruiterSummary;
import com.smartrecruit.company.domain.Company;
import com.smartrecruit.company.domain.RecruiterProfile;
import com.smartrecruit.company.infrastructure.CompanyRepository;
import com.smartrecruit.company.infrastructure.RecruiterProfileRepository;
import com.smartrecruit.identity.api.AccountAdministration;
import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.RecruiterCompanyLookup;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Entreprises : gestion par l'admin (création et consultation en phase 5 ; modification, désactivation
 * en cascade et réactivation en phase 10, US-49), lecture par les autres modules.
 * <p>
 * Implémente aussi {@link RecruiterCompanyLookup} : l'entreprise du recruteur entre dans son access token.
 */
@Service
public class CompanyService implements CompanyDirectory, RecruiterCompanyLookup {

    static final String COMPANY_CREATED = "COMPANY_CREATED";
    static final String COMPANY_UPDATED = "COMPANY_UPDATED";
    static final String COMPANY_DEACTIVATED = "COMPANY_DEACTIVATED";
    static final String COMPANY_ACTIVATED = "COMPANY_ACTIVATED";

    private final CompanyRepository companies;
    private final RecruiterProfileRepository recruiters;
    private final UserAccounts accounts;
    private final AccountAdministration accountAdministration;
    private final ObjectProvider<CompanyOffers> offers;
    private final AuditRecorder audit;

    public CompanyService(CompanyRepository companies, RecruiterProfileRepository recruiters, UserAccounts accounts,
                          AccountAdministration accountAdministration, ObjectProvider<CompanyOffers> offers,
                          AuditRecorder audit) {
        this.companies = companies;
        this.recruiters = recruiters;
        this.accounts = accounts;
        this.accountAdministration = accountAdministration;
        this.offers = offers;
        this.audit = audit;
    }

    /** Le nom unique est garanti par l'index {@code uk_company_name}, traduit en RESOURCE_IN_USE. */
    @Transactional
    public CompanyView create(Company.CompanyDetails details, CurrentUser admin) {
        Company company = companies.saveAndFlush(Company.create(details));
        audit.record(AuditEvent.success(COMPANY_CREATED).by(admin.id(), null).on("COMPANY", company.getId()));
        return view(company, 0, null);
    }

    /** Liste triée par nom, filtrée par état et par texte ; trois requêtes quelle que soit la page. */
    @Transactional(readOnly = true)
    public Page<CompanyView> search(Boolean active, String query, int page, int size) {
        Page<Company> found = companies.findAll(matching(active, query),
                PageRequest.of(page, size, Sort.by("name", "id")));
        List<UUID> ids = found.getContent().stream().map(Company::getId).toList();
        Map<UUID, Long> recruiterCounts = ids.isEmpty() ? Map.of() : recruiters.countByCompany(ids).stream()
                .collect(Collectors.toMap(RecruiterProfileRepository.CompanyCount::getCompanyId,
                        RecruiterProfileRepository.CompanyCount::getTotal));
        Map<UUID, OfferCounts> offerCounts = offerCounts(ids);
        return found.map(company -> view(company, recruiterCounts.getOrDefault(company.getId(), 0L),
                offerCounts.get(company.getId())));
    }

    @Transactional(readOnly = true)
    public CompanyView get(UUID id) {
        Company company = load(id);
        return view(company, recruiters.countByCompanyId(id), offerCounts(List.of(id)).get(id));
    }

    @Transactional
    public CompanyView update(UUID id, Company.CompanyDetails details, long version, CurrentUser admin) {
        Company company = load(id);
        if (company.getVersion() == null || company.getVersion() != version) {
            throw new OptimisticLockingFailureException("Entreprise " + id + " : version " + version + " obsolète");
        }
        company.update(details);
        companies.flush();
        audit.record(AuditEvent.success(COMPANY_UPDATED).by(admin.id(), null).on("COMPANY", id));
        return get(id);
    }

    /**
     * RG-31, dans une seule transaction : l'entreprise est désactivée, ses offres publiées sont
     * clôturées, ses recruteurs désactivés et leurs sessions révoquées. Si une étape échoue, rien
     * n'est appliqué. Les candidatures restent consultables par les candidats.
     */
    @Transactional
    public Deactivation deactivate(UUID id, CurrentUser admin) {
        Company company = load(id);
        if (!company.isActive()) {
            return new Deactivation(get(id), 0, 0);
        }
        company.deactivate();
        CompanyOffers jobs = offers.getIfAvailable();
        int closedJobs = jobs == null ? 0 : jobs.closePublished(id);
        int disabledRecruiters = accountAdministration.disableAll(
                recruiters.findByCompanyId(id).stream().map(RecruiterProfile::getUserId).toList(), admin.id(),
                COMPANY_DEACTIVATED);
        companies.flush();
        audit.record(AuditEvent.success(COMPANY_DEACTIVATED).by(admin.id(), null).on("COMPANY", id)
                .with("closedJobs", closedJobs).with("disabledRecruiters", disabledRecruiters));
        return new Deactivation(get(id), closedJobs, disabledRecruiters);
    }

    @Transactional
    public CompanyView activate(UUID id, CurrentUser admin) {
        Company company = load(id);
        if (!company.isActive()) {
            company.activate();
            companies.flush();
            audit.record(AuditEvent.success(COMPANY_ACTIVATED).by(admin.id(), null).on("COMPANY", id));
        }
        return get(id);
    }

    private Company load(UUID id) {
        return companies.findById(id).orElseThrow(() -> new ResourceNotFoundException("Entreprise"));
    }

    private Map<UUID, OfferCounts> offerCounts(List<UUID> ids) {
        CompanyOffers jobs = offers.getIfAvailable();
        return jobs == null || ids.isEmpty() ? Map.of() : jobs.countsOf(ids);
    }

    private static Specification<Company> matching(Boolean active, String query) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (active != null) {
                predicates.add(cb.equal(root.get("active"), active));
            }
            if (query != null && !query.isBlank()) {
                String text = query.strip().toLowerCase(Locale.ROOT)
                        .replace("!", "!!").replace("%", "!%").replace("_", "!_");
                predicates.add(cb.like(cb.lower(root.get("name")), "%" + text + "%", '!'));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<CompanySummary> find(UUID companyId) {
        return companies.findById(companyId).map(CompanyService::summary);
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, CompanySummary> findAll(Collection<UUID> companyIds) {
        return companies.findAllById(companyIds).stream()
                .map(CompanyService::summary)
                .collect(Collectors.toMap(CompanySummary::id, Function.identity()));
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<UUID> companyOf(UUID recruiterId) {
        return recruiters.findById(recruiterId).map(profile -> profile.getCompanyId());
    }

    private static CompanySummary summary(Company company) {
        return new CompanySummary(company.getId(), company.getName(), company.isActive(), company.getTimezone());
    }

    /** Deux requêtes (profils, comptes), quel que soit le nombre de recruteurs. */
    @Override
    @Transactional(readOnly = true)
    public List<RecruiterSummary> recruitersOf(UUID companyId) {
        List<RecruiterProfile> profiles = recruiters.findByCompanyId(companyId);
        Map<UUID, AccountSummary> names = accounts.findAll(profiles.stream().map(RecruiterProfile::getUserId).toList());
        return profiles.stream()
                .filter(profile -> names.containsKey(profile.getUserId()))
                .map(profile -> {
                    AccountSummary account = names.get(profile.getUserId());
                    return new RecruiterSummary(profile.getUserId(), account.firstName(), account.lastName(),
                            profile.getJobTitle());
                })
                .sorted(Comparator.comparing(RecruiterSummary::lastName, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(RecruiterSummary::firstName, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private static CompanyView view(Company company, long recruiterCount, OfferCounts offerCounts) {
        return new CompanyView(company, recruiterCount, offerCounts == null ? new OfferCounts(0, 0) : offerCounts);
    }

    /** Entreprise, nombre de recruteurs et d'offres, pour l'écran d'administration. */
    public record CompanyView(Company company, long recruiterCount, OfferCounts offerCounts) {
    }

    /** Résultat de la désactivation : ce que la cascade a fait, pour le message de confirmation. */
    public record Deactivation(CompanyView company, int closedJobs, int disabledRecruiters) {
    }
}
