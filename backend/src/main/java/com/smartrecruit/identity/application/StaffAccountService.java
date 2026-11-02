package com.smartrecruit.identity.application;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.identity.api.StaffAccountCreated;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.identity.infrastructure.UserRepository;
import com.smartrecruit.shared.domain.FieldValidationException;
import com.smartrecruit.shared.security.CurrentUser;
import com.smartrecruit.shared.security.Role;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * US-47 : création des comptes recruteur et admin par un administrateur. Le mot de passe temporaire
 * n'est renvoyé qu'une fois et jamais stocké en clair ; il devra être changé à la première connexion.
 */
@Service
public class StaffAccountService {

    static final String USER_CREATED = "USER_CREATED";

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final ApplicationEventPublisher events;
    private final AuditRecorder audit;

    public StaffAccountService(UserRepository users, PasswordEncoder passwordEncoder,
                               ApplicationEventPublisher events, AuditRecorder audit) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.events = events;
        this.audit = audit;
    }

    @Transactional
    public CreatedStaffAccount create(CreateStaffCommand command, CurrentUser admin) {
        checkRoleAndCompany(command);
        String email = User.normalizeEmail(command.email());
        if (users.existsByEmail(email)) {
            throw AuthService.emailAlreadyUsed();
        }
        String temporaryPassword = TemporaryPasswords.generate();
        User user = users.saveAndFlush(User.createStaff(email, passwordEncoder.encode(temporaryPassword),
                command.firstName(), command.lastName(), command.role()));
        // Le module Entreprises vérifie l'entreprise et crée le profil recruteur, dans cette transaction
        events.publishEvent(new StaffAccountCreated(user.getId(), user.getRole(), command.companyId(),
                command.jobTitle()));
        audit.record(AuditEvent.success(USER_CREATED).by(admin.id(), null).on("USER", user.getId())
                .with("role", user.getRole().name())
                .with("companyId", command.companyId() == null ? "" : command.companyId().toString()));
        return new CreatedStaffAccount(user, command.companyId(), temporaryPassword);
    }

    /** Un recruteur appartient à une entreprise (RG-05) ; un admin à aucune. */
    private static void checkRoleAndCompany(CreateStaffCommand command) {
        if (command.role() == Role.CANDIDATE) {
            throw new FieldValidationException("role", "Un candidat crée lui-même son compte.");
        }
        if (command.role() == Role.RECRUITER && command.companyId() == null) {
            throw new FieldValidationException("companyId", "Obligatoire pour un recruteur.");
        }
        if (command.role() == Role.ADMIN && command.companyId() != null) {
            throw new FieldValidationException("companyId", "Un administrateur n'appartient à aucune entreprise.");
        }
    }

    public record CreateStaffCommand(String email, String firstName, String lastName, Role role, UUID companyId,
                                     String jobTitle) {
    }

    /** @param temporaryPassword en clair, à afficher une seule fois à l'admin */
    public record CreatedStaffAccount(User user, UUID companyId, String temporaryPassword) {
    }
}
