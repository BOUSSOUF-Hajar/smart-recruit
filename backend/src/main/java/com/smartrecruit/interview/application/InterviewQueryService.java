package com.smartrecruit.interview.application;

import com.smartrecruit.application.api.ApplicationDirectory;
import com.smartrecruit.application.api.ApplicationRef;
import com.smartrecruit.company.api.CompanyDirectory;
import com.smartrecruit.company.api.CompanySummary;
import com.smartrecruit.identity.api.AccountSummary;
import com.smartrecruit.identity.api.UserAccounts;
import com.smartrecruit.interview.domain.Interview;
import com.smartrecruit.interview.infrastructure.InterviewCriteria;
import com.smartrecruit.interview.infrastructure.InterviewRepository;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.web.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/** Lectures des entretiens : agenda et détail recruteur (F-INT-05), « mes entretiens » du candidat (US-38). */
@Service
@Transactional(readOnly = true)
public class InterviewQueryService {

    private final InterviewRepository interviews;
    private final ApplicationDirectory applications;
    private final CompanyDirectory companies;
    private final UserAccounts accounts;
    private final Clock clock;

    public InterviewQueryService(InterviewRepository interviews, ApplicationDirectory applications,
                                 CompanyDirectory companies, UserAccounts accounts, Clock clock) {
        this.interviews = interviews;
        this.applications = applications;
        this.companies = companies;
        this.accounts = accounts;
        this.clock = clock;
    }

    /** Agenda : à venir d'abord ; participants, candidats et offres chargés en un nombre fixe de requêtes. */
    public PageResponse<RecruiterInterview> agenda(InterviewCriteria criteria, int page, int size) {
        Page<Interview> found = interviews.findAll(criteria.toSpecification(), PageRequest.of(PageResponse.page(page),
                PageResponse.size(size), Sort.by(Sort.Order.asc("startsAt"), Sort.Order.asc("id"))));
        return PageResponse.of(found.getContent().isEmpty() ? List.<RecruiterInterview>of()
                : recruiterViews(found.getContent()), found.getNumber(), found.getSize(), found.getTotalElements());
    }

    public RecruiterInterview detail(CurrentUser recruiter, UUID id) {
        if (recruiter.companyId() == null) {
            throw new ResourceNotFoundException("Entretien");
        }
        Interview interview = interviews.findByIdAndCompanyId(id, recruiter.companyId())
                .orElseThrow(() -> new ResourceNotFoundException("Entretien"));
        return recruiterViews(List.of(interview)).getFirst();
    }

    /**
     * Relecture après une écriture déjà autorisée : dans une nouvelle transaction, car l'entité
     * renvoyée par l'écriture est détachée (ses participants ne sont plus chargeables).
     */
    public RecruiterInterview reread(UUID id) {
        return recruiterViews(List.of(interviews.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Entretien")))).getFirst();
    }

    /**
     * US-38 : à venir (les prochains d'abord) ou passés (les plus récents d'abord). Les entretiens
     * annulés restent listés, avec leur statut. Ni avis, ni participants (RG-23).
     */
    public List<CandidateInterview> candidateInterviews(CurrentUser candidate, boolean upcoming) {
        List<UUID> applicationIds = applications.idsOfCandidate(candidate.id());
        if (applicationIds.isEmpty()) {
            return List.of();
        }
        List<Interview> found = upcoming
                ? interviews.findByApplicationIdInAndStartsAtGreaterThanEqualOrderByStartsAtAsc(applicationIds,
                        clock.instant())
                : interviews.findByApplicationIdInAndStartsAtLessThanOrderByStartsAtDesc(applicationIds,
                        clock.instant());
        Map<UUID, ApplicationRef> refs = applications.findAll(
                found.stream().map(Interview::getApplicationId).collect(Collectors.toSet()));
        Map<UUID, CompanySummary> companyNames = companies.findAll(
                refs.values().stream().map(ApplicationRef::companyId).collect(Collectors.toSet()));
        return found.stream().map(interview -> {
            ApplicationRef ref = refs.get(interview.getApplicationId());
            CompanySummary company = ref == null ? null : companyNames.get(ref.companyId());
            return new CandidateInterview(interview, ref == null ? "" : ref.jobTitle(),
                    company == null ? "" : company.name());
        }).toList();
    }

    private List<RecruiterInterview> recruiterViews(List<Interview> found) {
        Map<UUID, ApplicationRef> refs = applications.findAll(
                found.stream().map(Interview::getApplicationId).collect(Collectors.toSet()));
        Set<UUID> people = found.stream()
                .flatMap(interview -> Stream.concat(interview.getParticipantIds().stream(),
                        Stream.of(interview.getScheduledBy())))
                .collect(Collectors.toSet());
        Map<UUID, AccountSummary> names = accounts.findAll(people);
        return found.stream().map(interview -> new RecruiterInterview(interview,
                refs.get(interview.getApplicationId()),
                interview.getParticipantIds().stream().map(id -> new Person(id, name(names, id)))
                        .sorted(Comparator.comparing(Person::name)).toList(),
                new Person(interview.getScheduledBy(), name(names, interview.getScheduledBy())))).toList();
    }

    private static String name(Map<UUID, AccountSummary> names, UUID id) {
        AccountSummary account = names.get(id);
        return account == null ? "" : (account.firstName() + " " + account.lastName()).strip();
    }

    public record Person(UUID id, String name) {
    }

    /** @param application candidature (nom du candidat, offre) ; {@code null} seulement en cas d'incohérence */
    public record RecruiterInterview(Interview interview, ApplicationRef application, List<Person> participants,
                                     Person scheduledBy) {
    }

    public record CandidateInterview(Interview interview, String jobTitle, String companyName) {
    }
}
