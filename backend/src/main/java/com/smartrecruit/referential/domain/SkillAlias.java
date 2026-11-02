package com.smartrecruit.referential.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.Objects;
import java.util.UUID;

/** Autre forme d'une compétence (« springboot » → Spring Boot), créée par les données de départ ou une fusion. */
@Entity
@Table(name = "skill_alias")
public class SkillAlias {

    @Id
    @Column(name = "normalized_alias", length = Skill.MAX_NAME_LENGTH)
    private String normalizedAlias;

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    protected SkillAlias() {
        // JPA
    }

    public static SkillAlias of(String normalizedAlias, UUID skillId) {
        SkillAlias alias = new SkillAlias();
        alias.normalizedAlias = Objects.requireNonNull(normalizedAlias, "normalizedAlias");
        alias.skillId = Objects.requireNonNull(skillId, "skillId");
        return alias;
    }

    public String getNormalizedAlias() {
        return normalizedAlias;
    }

    public UUID getSkillId() {
        return skillId;
    }
}
