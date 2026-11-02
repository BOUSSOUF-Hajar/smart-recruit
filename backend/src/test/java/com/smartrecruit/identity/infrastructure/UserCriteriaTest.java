package com.smartrecruit.identity.infrastructure;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** US-48 : la recherche de comptes ignore la casse et les accents. */
class UserCriteriaTest {

    @Test
    void searchTextIsLowercasedWithoutAccents() {
        assertThat(UserCriteria.normalize("  ÉLODIE Bérard ")).isEqualTo("elodie berard");
        assertThat(UserCriteria.normalize(null)).isEmpty();
    }
}
