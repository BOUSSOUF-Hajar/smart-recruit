package com.smartrecruit.identity.domain;

import com.smartrecruit.shared.persistence.VersionedEntity;
import com.smartrecruit.shared.security.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * Compte utilisateur. Les règles qui ne dépendent que du compte sont ici (RG-01 à RG-03) ;
 * celles qui demandent la base ou la sécurité (unicité de l'email, hachage) sont dans les services.
 */
@Entity
@Table(name = "app_user")
public class User extends VersionedEntity {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 254)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "first_name", nullable = false, length = 80)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 80)
    private String lastName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "must_change_password", nullable = false)
    private boolean mustChangePassword;

    @Column(name = "privacy_accepted_at")
    private Instant privacyAcceptedAt;

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    /** Incrémentée pour invalider tous les access tokens déjà émis (US-48). */
    @Column(name = "session_version", nullable = false)
    private int sessionVersion;

    protected User() {
        // JPA
    }

    private User(String email, String passwordHash, String firstName, String lastName, Role role) {
        this.email = normalizeEmail(email);
        this.passwordHash = Objects.requireNonNull(passwordHash, "passwordHash");
        this.firstName = firstName.strip();
        this.lastName = lastName.strip();
        this.role = Objects.requireNonNull(role, "role");
    }

    /** US-06 : inscription publique, toujours en CANDIDATE (RG-02), avec consentement daté (RGPD). */
    public static User registerCandidate(String email, String passwordHash, String firstName, String lastName,
                                         Instant privacyAcceptedAt) {
        User user = new User(email, passwordHash, firstName, lastName, Role.CANDIDATE);
        user.privacyAcceptedAt = Objects.requireNonNull(privacyAcceptedAt, "privacyAcceptedAt");
        return user;
    }

    /**
     * Compte créé par un administrateur (recruteur ou admin) : le mot de passe est temporaire
     * et doit être changé à la première connexion (RG-03, US-11).
     */
    public static User createStaff(String email, String temporaryPasswordHash, String firstName, String lastName,
                                   Role role) {
        if (role == Role.CANDIDATE) {
            throw new IllegalArgumentException("Un candidat s'inscrit lui-même");
        }
        User user = new User(email, temporaryPasswordHash, firstName, lastName, role);
        user.mustChangePassword = true;
        return user;
    }

    /** Emails comparés sans tenir compte de la casse ni des espaces autour (RG-01). */
    public static String normalizeEmail(String email) {
        return email.strip().toLowerCase(Locale.ROOT);
    }

    public void rename(String firstName, String lastName) {
        this.firstName = Objects.requireNonNull(firstName, "firstName").strip();
        this.lastName = Objects.requireNonNull(lastName, "lastName").strip();
    }

    public void changePassword(String newPasswordHash) {
        this.passwordHash = Objects.requireNonNull(newPasswordHash, "newPasswordHash");
        this.mustChangePassword = false;
    }

    /** Remplace le hash par un hash plus fort, sans toucher au marqueur de changement obligatoire. */
    public void rehashPassword(String strongerHash) {
        this.passwordHash = Objects.requireNonNull(strongerHash, "strongerHash");
    }

    public void recordLogin(Instant at) {
        this.lastLoginAt = at;
    }

    /** RG-03 : plus de connexion ni de renouvellement ; les tokens déjà émis sont invalidés. */
    public void disable() {
        this.enabled = false;
        revokeSessions();
    }

    public void enable() {
        this.enabled = true;
    }

    /** Les access tokens émis avant cet appel sont refusés dès la requête suivante. */
    public void revokeSessions() {
        this.sessionVersion++;
    }

    public int getSessionVersion() {
        return sessionVersion;
    }

    public UUID getId() {
        return id;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getFirstName() {
        return firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public Role getRole() {
        return role;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public Instant getPrivacyAcceptedAt() {
        return privacyAcceptedAt;
    }

    public Instant getLastLoginAt() {
        return lastLoginAt;
    }
}
