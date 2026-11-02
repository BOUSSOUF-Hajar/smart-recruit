package com.smartrecruit.job.domain;

import com.smartrecruit.referential.api.SkillLevel;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import java.util.Objects;
import java.util.UUID;

/** Compétence d'une offre : requise avec un niveau minimum, ou souhaitée sans niveau. */
@Embeddable
public class JobSkill {

    @Column(name = "skill_id", nullable = false)
    private UUID skillId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SkillRequirement requirement;

    @Enumerated(EnumType.STRING)
    @Column(name = "min_level", length = 16)
    private SkillLevel minLevel;

    protected JobSkill() {
        // JPA
    }

    private JobSkill(UUID skillId, SkillRequirement requirement, SkillLevel minLevel) {
        this.skillId = Objects.requireNonNull(skillId, "skillId");
        this.requirement = requirement;
        this.minLevel = minLevel;
    }

    public static JobSkill required(UUID skillId, SkillLevel minLevel) {
        return new JobSkill(skillId, SkillRequirement.REQUIRED, Objects.requireNonNull(minLevel, "minLevel"));
    }

    public static JobSkill preferred(UUID skillId) {
        return new JobSkill(skillId, SkillRequirement.PREFERRED, null);
    }

    public boolean isRequired() {
        return requirement == SkillRequirement.REQUIRED;
    }

    public UUID getSkillId() {
        return skillId;
    }

    public SkillRequirement getRequirement() {
        return requirement;
    }

    public SkillLevel getMinLevel() {
        return minLevel;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof JobSkill that && skillId.equals(that.skillId) && requirement == that.requirement
                && minLevel == that.minLevel;
    }

    @Override
    public int hashCode() {
        return Objects.hash(skillId, requirement, minLevel);
    }
}
