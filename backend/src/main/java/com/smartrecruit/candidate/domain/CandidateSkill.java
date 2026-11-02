package com.smartrecruit.candidate.domain;

import com.smartrecruit.referential.api.SkillLevel;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;
import java.util.UUID;

/** Compétence du profil : référence au référentiel (par identifiant, autre module), niveau, années. */
@Embeddable
public class CandidateSkill {

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SkillLevel level;

    @Column
    private Short years;

    protected CandidateSkill() {
        // JPA
    }

    public CandidateSkill(UUID skillId, SkillLevel level, Integer years) {
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.level = Objects.requireNonNull(level, "level");
        this.years = years == null ? null : years.shortValue();
    }

    public UUID getSkillId() {
        return skillId;
    }

    public SkillLevel getLevel() {
        return level;
    }

    public Integer getYears() {
        return years == null ? null : years.intValue();
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof CandidateSkill that && skillId.equals(that.skillId) && level == that.level
                && Objects.equals(years, that.years);
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillId, level, years);
    }
}
