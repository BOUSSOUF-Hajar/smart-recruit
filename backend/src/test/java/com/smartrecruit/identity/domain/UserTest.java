package com.smartrecruit.identity.domain;

import com.smartrecruit.shared.security.Role;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UserTest {

    private static final Instant NOW = Instant.parse("2026-10-19T10:00:00Z");

    @Test
    void registrationAlwaysCreatesAnEnabledCandidateWithNormalizedEmail() {
        User user = User.registerCandidate("  Salma.Idrissi@Example.COM ", "{bcrypt}hash", " Salma ", "Idrissi", NOW);

        assertThat(user.getEmail()).isEqualTo("salma.idrissi@example.com");
        assertThat(user.getFirstName()).isEqualTo("Salma");
        assertThat(user.getRole()).isEqualTo(Role.CANDIDATE);
        assertThat(user.isEnabled()).isTrue();
        assertThat(user.isMustChangePassword()).isFalse();
        assertThat(user.getPrivacyAcceptedAt()).isEqualTo(NOW);
    }

    @Test
    void staffAccountsMustChangeTheirTemporaryPassword() {
        User recruiter = User.createStaff("rh@acme.ma", "{bcrypt}temp", "Youssef", "Alami", Role.RECRUITER);

        assertThat(recruiter.isMustChangePassword()).isTrue();

        recruiter.changePassword("{bcrypt}new");

        assertThat(recruiter.isMustChangePassword()).isFalse();
        assertThat(recruiter.getPasswordHash()).isEqualTo("{bcrypt}new");
    }

    @Test
    void rehashingKeepsThePasswordChangeObligation() {
        User admin = User.createStaff("admin@acme.ma", "{noop}temp", "Admin", "Acme", Role.ADMIN);

        admin.rehashPassword("{bcrypt}stronger");

        assertThat(admin.isMustChangePassword()).isTrue();
    }

    @Test
    void candidatesCannotBeCreatedAsStaff() {
        assertThatThrownBy(() -> User.createStaff("c@example.com", "h", "C", "D", Role.CANDIDATE))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
