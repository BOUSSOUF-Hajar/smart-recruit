package com.smartrecruit.identity.application;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.identity.api.AccountAdministration;
import com.smartrecruit.identity.api.RecruiterAffiliations;
import com.smartrecruit.identity.api.RecruiterAffiliations.Affiliation;
import com.smartrecruit.identity.domain.RevocationReason;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.RefreshTokenRepository;
import com.smartrecruit.identity.infrastructure.UserCriteria;
import com.smartrecruit.identity.infrastructure.UserRepository;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.security.Role;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * US-48 : recherche, modification, désactivation et réactivation des comptes par l'admin.
 * <p>
 * Désactiver un compte (RG-03) le bloque à la connexion et au renouvellement, révoque ses refresh
 * tokens et invalide ses access tokens dès la requête suivante (version de session). RG-29 : un admin
 * ne se désactive pas lui-même, et le dernier admin actif ne peut pas être désactivé.
 */
@Service
public class AdminUserService implements AccountAdministration {

    static final String USER_UPDATED = "USER_UPDATED";
    static final String USER_DISABLED = "USER_DISABLED";
    static final String USER_ENABLED = "USER_ENABLED";

    private static final Sort BY_NAME = Sort.by("lastName", "firstName", "id");

    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final AccessTokenRevocations revocations;
    private final ObjectProvider<RecruiterAffiliations> affiliations;
    private final AuditRecorder audit;
    private final Clock clock;

    public AdminUserService(UserRepository users, RefreshTokenRepository refreshTokens,
                            AccessTokenRevocations revocations, ObjectProvider<RecruiterAffiliations> affiliations,
                            AuditRecorder audit, Clock clock) {
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.revocations = revocations;
        this.affiliations = affiliations;
        this.audit = audit;
        this.clock = clock;
    }

    /** Liste filtrée et paginée, triée par nom ; trois requêtes quelle que soit la taille de la page. */
    @Transactional(readOnly = true)
    public Page<AdminUserView> search(SearchCriteria criteria, int page, int size) {
        Set<UUID> restrictTo = criteria.companyId() == null ? null : affiliations().recruitersOf(criteria.companyId());
        if (restrictTo != null && restrictTo.isEmpty()) {
            return Page.empty(PageRequest.of(page, size, BY_NAME));
        }
        Page<User> found = users.findAll(UserCriteria.matching(criteria.role(), criteria.enabled(), criteria.query(),
                restrictTo), PageRequest.of(page, size, BY_NAME));
        Map<UUID, Affiliation> byUser = affiliationsOf(found.getContent());
        return found.map(user -> new AdminUserView(user, byUser.get(user.getId())));
    }

    @Transactional(readOnly = true)
    public AdminUserView get(UUID id) {
        User user = find(id);
        return new AdminUserView(user, affiliationsOf(List.of(user)).get(id));
    }

    /**
     * Nom, entreprise et poste ; jamais le rôle (G-3). Changer l'entreprise d'un recruteur révoque ses
     * sessions : son token porte l'entreprise, il ne doit plus voir les candidatures de l'ancienne.
     */
    @Transactional
    public AdminUserView update(UUID id, UpdateCommand command, CurrentUser admin) {
        User user = find(id);
        if (user.getVersion() == null || user.getVersion() != command.version()) {
            throw new OptimisticLockingFailureException("Compte " + id + " : version " + command.version()
                    + " obsolète (actuelle " + user.getVersion() + ")");
        }
        user.rename(command.firstName(), command.lastName());
        boolean companyChanged = false;
        if (user.getRole() == Role.RECRUITER) {
            if (command.companyId() == null) {
                throw new FieldValidationException("companyId", "Obligatoire pour un recruteur.");
            }
            companyChanged = affiliations().update(id, command.companyId(), command.jobTitle());
        } else if (command.companyId() != null) {
            throw new FieldValidationException("companyId", "Seul un recruteur appartient à une entreprise.");
        }
        if (companyChanged) {
            revokeAllSessions(user, RevocationReason.COMPANY_CHANGED);
        }
        users.flush();
        audit.record(AuditEvent.success(USER_UPDATED).by(admin.id(), null).on("USER", id)
                .with("companyChanged", companyChanged));
        return new AdminUserView(user, affiliationsOf(List.of(user)).get(id));
    }

    @Transactional
    public AdminUserView disable(UUID id, CurrentUser admin) {
        if (id.equals(admin.id())) {
            throw new InvalidStateException(ErrorCode.LAST_ADMIN, "Vous ne pouvez pas désactiver votre propre compte.");
        }
        User user = find(id);
        if (user.isEnabled()) {
            if (user.getRole() == Role.ADMIN && users.lockActiveAdmins().size() <= 1) {
                throw new InvalidStateException(ErrorCode.LAST_ADMIN,
                        "C'est le dernier administrateur actif : il ne peut pas être désactivé.");
            }
            disableAndAudit(user, admin.id(), null);
        }
        users.flush();
        return new AdminUserView(user, affiliationsOf(List.of(user)).get(id));
    }

    /** Un recruteur d'une entreprise désactivée reste désactivé (RG-31) : on réactive l'entreprise d'abord. */
    @Transactional
    public AdminUserView enable(UUID id, CurrentUser admin) {
        User user = find(id);
        Affiliation affiliation = affiliationsOf(List.of(user)).get(id);
        if (!user.isEnabled()) {
            if (affiliation != null && !affiliation.companyActive()) {
                throw new InvalidStateException(ErrorCode.INVALID_STATE,
                        "L'entreprise de ce recruteur est désactivée : réactivez-la d'abord.");
            }
            user.enable();
            audit.record(AuditEvent.success(USER_ENABLED).by(admin.id(), null).on("USER", id));
        }
        users.flush();
        return new AdminUserView(user, affiliation);
    }

    /** RG-31 : appelée par le module Entreprises quand une entreprise est désactivée. */
    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public int disableAll(Collection<UUID> userIds, UUID actorId, String reason) {
        List<User> active = users.findAllById(userIds).stream().filter(User::isEnabled).toList();
        active.forEach(user -> disableAndAudit(user, actorId, reason));
        return active.size();
    }

    private void disableAndAudit(User user, UUID actorId, String reason) {
        user.disable();
        refreshTokens.revokeAllForUser(user.getId(), clock.instant(), RevocationReason.ACCOUNT_DISABLED);
        revocations.register(user);
        AuditEvent event = AuditEvent.success(USER_DISABLED).by(actorId, null).on("USER", user.getId());
        audit.record(reason == null ? event : event.with("reason", reason));
    }

    private void revokeAllSessions(User user, RevocationReason reason) {
        user.revokeSessions();
        refreshTokens.revokeAllForUser(user.getId(), clock.instant(), reason);
        revocations.register(user);
    }

    private User find(UUID id) {
        return users.findById(id).orElseThrow(() -> new ResourceNotFoundException("Compte"));
    }

    private Map<UUID, Affiliation> affiliationsOf(List<User> found) {
        List<UUID> recruiterIds = found.stream().filter(user -> user.getRole() == Role.RECRUITER)
                .map(User::getId).toList();
        return recruiterIds.isEmpty() ? Map.of() : affiliations().of(recruiterIds);
    }

    private RecruiterAffiliations affiliations() {
        return affiliations.getObject();
    }

    /** @param query recherche sur le nom ou l'email, sans tenir compte de la casse ni des accents */
    public record SearchCriteria(Role role, UUID companyId, Boolean enabled, String query) {
    }

    public record UpdateCommand(String firstName, String lastName, UUID companyId, String jobTitle, long version) {
    }

    /** @param affiliation entreprise et poste d'un recruteur ; {@code null} pour les autres rôles */
    public record AdminUserView(User user, Affiliation affiliation) {
    }
}
