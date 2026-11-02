package com.smartrecruit.referential.application;

import com.smartrecruit.referential.domain.Skill;

import java.util.UUID;

public record SkillView(UUID id, String name) {

    static SkillView of(Skill skill) {
        return new SkillView(skill.getId(), skill.getName());
    }
}
