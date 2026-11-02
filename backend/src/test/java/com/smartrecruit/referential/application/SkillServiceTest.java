package com.smartrecruit.referential.application;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SkillServiceTest {

    @Test
    void likeWildcardsTypedByTheUserAreEscaped() {
        // « 100% » ne doit pas devenir « tout ce qui commence par 100 »
        assertThat(SkillService.escapeLike("100%")).isEqualTo("100!%");
        assertThat(SkillService.escapeLike("c_sharp")).isEqualTo("c!_sharp");
        assertThat(SkillService.escapeLike("wow!")).isEqualTo("wow!!");
    }
}
