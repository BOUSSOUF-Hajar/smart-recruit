package com.smartrecruit.interview.infrastructure;

import com.smartrecruit.interview.domain.Interview;
import com.smartrecruit.interview.domain.InterviewStatus;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Filtres de l'agenda recruteur (F-INT-05, docs/api.md §8), combinés en une Specification : seuls les
 * filtres présents deviennent des conditions.
 *
 * @param participantId « mes entretiens » : seulement ceux où ce recruteur participe
 */
public record InterviewCriteria(UUID companyId, Instant from, Instant to, Set<InterviewStatus> statuses,
                                UUID jobOfferId, UUID applicationId, UUID participantId) {

    public InterviewCriteria {
        statuses = statuses == null ? Set.of() : Set.copyOf(statuses);
    }

    public Specification<Interview> toSpecification() {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("companyId"), companyId));   // RG-05, toujours
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("startsAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThan(root.get("startsAt"), to));
            }
            if (!statuses.isEmpty()) {
                predicates.add(root.get("status").in(statuses));
            }
            if (jobOfferId != null) {
                predicates.add(cb.equal(root.get("jobOfferId"), jobOfferId));
            }
            if (applicationId != null) {
                predicates.add(cb.equal(root.get("applicationId"), applicationId));
            }
            if (participantId != null) {
                predicates.add(cb.isMember(participantId, root.<Collection<UUID>>get("participantIds")));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
