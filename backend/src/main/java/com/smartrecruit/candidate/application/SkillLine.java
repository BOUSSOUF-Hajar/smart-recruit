package com.smartrecruit.candidate.application;

import com.smartrecruit.referential.api.SkillLevel;

import java.util.UUID;

/** Compétence du profil avec son nom, lu dans le référentiel au moment de l'affichage. */
public record SkillLine(UUID skillId, String name, SkillLevel level, Integer years) {
}
