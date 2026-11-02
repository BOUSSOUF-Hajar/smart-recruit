package com.smartrecruit.referential.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Compétence du référentiel commun, partagée par profils, offres et matching. */
@Entity
@Table(name = "skill")
public class Skill {

    public static final int MAX_NAME_LENGTH = 80;

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = MAX_NAME_LENGTH)
    private String name;

    @Column(name = "normalized_name", nullable = false, length = MAX_NAME_LENGTH)
    private String normalizedName;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by")
    private UUID createdBy;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected Skill() {
        // JPA
    }

    /** Création par un utilisateur (US-15) : la forme normalisée est calculée ici, jamais fournie. */
    public static Skill create(String rawName, UUID createdBy, Instant now) {
        Skill skill = new Skill();
        skill.name = SkillNames.display(rawName);
        skill.normalizedName = SkillNames.normalize(rawName);
        skill.createdBy = createdBy;
        skill.createdAt = Objects.requireNonNull(now, "now");
        return skill;
    }

    /** US-51 : nouvelle forme affichée ; la forme normalisée suit. */
    public void rename(String rawName) {
        this.name = SkillNames.display(rawName);
        this.normalizedName = SkillNames.normalize(rawName);
    }

    /** RG-30 : plus proposée à l'autocomplétion ; les profils et offres qui l'ont la gardent. */
    public void deactivate() {
        this.active = false;
    }

    public void activate() {
        this.active = true;
    }

    public UUID getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getNormalizedName() {
        return normalizedName;
    }

    public boolean isActive() {
        return active;
    }

    public UUID getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
