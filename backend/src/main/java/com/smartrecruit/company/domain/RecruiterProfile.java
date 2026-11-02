package com.smartrecruit.company.domain;

import com.smartrecruit.shared.persistence.TimestampedEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/**
 * Rattachement d'un compte recruteur à son entreprise (même identifiant que le compte).
 * La base ne peut pas vérifier que le compte a bien le rôle RECRUITER (règle sur deux tables) :
 * seul le service de création de compte crée ce profil.
 */
@Entity
@Table(name = "recruiter_profile")
public class RecruiterProfile extends TimestampedEntity {

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @Column(name = "job_title", length = 120)
    private String jobTitle;

    protected RecruiterProfile() {
        // JPA
    }

    public static RecruiterProfile create(UUID userId, UUID companyId, String jobTitle) {
        RecruiterProfile profile = new RecruiterProfile();
        profile.userId = Objects.requireNonNull(userId, "userId");
        profile.companyId = Objects.requireNonNull(companyId, "companyId");
        profile.jobTitle = jobTitle == null || jobTitle.isBlank() ? null : jobTitle.strip();
        return profile;
    }

    /**
     * US-48 : rattachement à une autre entreprise ou nouveau poste.
     *
     * @return vrai si l'entreprise a changé
     */
    public boolean reassign(UUID newCompanyId, String newJobTitle) {
        boolean companyChanged = !companyId.equals(Objects.requireNonNull(newCompanyId, "newCompanyId"));
        companyId = newCompanyId;
        jobTitle = newJobTitle == null || newJobTitle.isBlank() ? null : newJobTitle.strip();
        return companyChanged;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getCompanyId() {
        return companyId;
    }

    public String getJobTitle() {
        return jobTitle;
    }
}
