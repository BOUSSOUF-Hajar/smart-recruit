package com.smartrecruit.referential.api;

import java.util.UUID;

/** Compétence vue par les autres modules ; {@code active} faux = plus proposée (RG-30). */
public record SkillSummary(UUID id, String name, boolean active) {
}
