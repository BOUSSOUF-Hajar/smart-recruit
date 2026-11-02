package com.smartrecruit.referential.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** US-15 : une même compétence, quelle que soit la saisie. */
class SkillNamesTest {

    @Test
    void normalizedFormIgnoresCaseAndExtraSpaces() {
        assertThat(SkillNames.normalize("  Spring   BOOT ")).isEqualTo("spring boot");
        assertThat(SkillNames.normalize("Spring\tBoot")).isEqualTo("spring boot");
    }

    @Test
    void displayFormKeepsCase() {
        assertThat(SkillNames.display("  Spring   Boot ")).isEqualTo("Spring Boot");
    }

    @Test
    void compatibilityCharactersAreUnified() {
        // Espace insécable et chiffre pleine chasse, fréquents dans un copier-coller
        assertThat(SkillNames.normalize("Java\u00A0１７")).isEqualTo("java 17");
    }

    @Test
    void createdSkillComputesBothForms() {
        Skill skill = Skill.create(" Quarkus  Native ", null, java.time.Instant.EPOCH);

        assertThat(skill.getName()).isEqualTo("Quarkus Native");
        assertThat(skill.getNormalizedName()).isEqualTo("quarkus native");
        assertThat(skill.isActive()).isTrue();
    }
}
