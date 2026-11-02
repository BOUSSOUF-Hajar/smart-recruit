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

/** Catégorie d'offre, gérée par l'admin (US-50) ; les données de départ en créent une douzaine. */
@Entity
@Table(name = "job_category")
public class JobCategory {

    @Id
    @GeneratedValue
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    private UUID id;

    @Column(nullable = false, length = 80)
    private String name;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected JobCategory() {
        // JPA
    }

    public static JobCategory create(String name, Instant now) {
        JobCategory category = new JobCategory();
        category.rename(name);
        category.createdAt = Objects.requireNonNull(now, "now");
        return category;
    }

    /** L'unicité, sans tenir compte de la casse, est garantie par l'index {@code uk_job_category_name}. */
    public void rename(String newName) {
        this.name = Objects.requireNonNull(newName, "name").strip();
    }

    /** RG-30 : plus proposée pour les nouvelles offres ; les offres existantes la gardent. */
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

    public boolean isActive() {
        return active;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
