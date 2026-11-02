package com.smartrecruit.identity.infrastructure;

import com.smartrecruit.audit.api.AuditEvent;
import com.smartrecruit.audit.api.AuditRecorder;
import com.smartrecruit.identity.domain.CommonPasswords;
import com.smartrecruit.identity.domain.User;
import com.smartrecruit.shared.security.Role;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Crée le premier administrateur au démarrage s'il n'en existe aucun (critère repris d'US-02 dans US-07).
 * <p>
 * Le mot de passe fourni est temporaire : l'administrateur doit le changer à sa première connexion.
 * Une fois un admin présent, les variables sont ignorées : les redémarrages ne recréent rien
 * et ne réinitialisent aucun mot de passe.
 */
@Component
class InitialAdminBootstrap implements ApplicationRunner {

    static final String USER_CREATED = "USER_CREATED";
    private static final Logger log = LoggerFactory.getLogger(InitialAdminBootstrap.class);

    private final BootstrapAdminProperties properties;
    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuditRecorder audit;

    InitialAdminBootstrap(BootstrapAdminProperties properties, UserRepository users,
                          PasswordEncoder passwordEncoder, AuditRecorder audit) {
        this.properties = properties;
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.audit = audit;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (users.existsByRole(Role.ADMIN)) {
            return;
        }
        if (!properties.isConfigured()) {
            log.warn("Aucun administrateur : définissez INITIAL_ADMIN_EMAIL et INITIAL_ADMIN_PASSWORD puis redémarrez.");
            return;
        }
        String password = properties.password();
        if (password.length() < 12 || CommonPasswords.contains(password)) {
            // Échec du démarrage plutôt qu'un compte admin protégé par un mot de passe faible
            throw new IllegalStateException(
                    "INITIAL_ADMIN_PASSWORD doit faire au moins 12 caractères et ne pas être un mot de passe courant.");
        }
        User admin = users.save(User.createStaff(properties.email(), passwordEncoder.encode(password),
                "Administrateur", "SmartRecruit", Role.ADMIN));
        audit.record(AuditEvent.success(USER_CREATED).by(null, "system").on("USER", admin.getId())
                .with("role", Role.ADMIN.name()).with("source", "bootstrap"));
        log.info("Administrateur initial créé : {} (mot de passe à changer à la première connexion)", admin.getEmail());
    }
}
