package com.smartrecruit.identity.infrastructure;

import com.smartrecruit.identity.domain.User;
import com.smartrecruit.shared.security.Role;
import jakarta.persistence.criteria.Expression;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.jpa.domain.Specification;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/** Filtres de la liste des comptes (US-48), combinés par ET ; un filtre absent n'est pas appliqué. */
public final class UserCriteria {

    private UserCriteria() {
    }

    /**
     * @param query   texte cherché dans l'email ou le nom complet, sans casse ni accents
     * @param userIds restreint aux comptes listés (filtre par entreprise) ; {@code null} = pas de restriction
     */
    public static Specification<User> matching(Role role, Boolean enabled, String query, Collection<UUID> userIds) {
        return (root, criteriaQuery, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (role != null) {
                predicates.add(cb.equal(root.get("role"), role));
            }
            if (enabled != null) {
                predicates.add(cb.equal(root.get("enabled"), enabled));
            }
            if (userIds != null) {
                predicates.add(root.get("id").in(userIds));
            }
            String text = normalize(query);
            if (!text.isEmpty()) {
                String pattern = "%" + text.replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
                Expression<String> fullName = cb.concat(cb.concat(root.get("firstName"), " "), root.get("lastName"));
                predicates.add(cb.or(
                        cb.like(root.get("email"), pattern, '!'),
                        cb.like(cb.lower(cb.function("immutable_unaccent", String.class, fullName)), pattern, '!')));
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }

    /** Minuscules sans accents : « Él » → « el ». L'email est déjà stocké en minuscules (RG-01). */
    static String normalize(String query) {
        if (query == null) {
            return "";
        }
        String decomposed = Normalizer.normalize(query.strip(), Normalizer.Form.NFD);
        return decomposed.replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }
}
