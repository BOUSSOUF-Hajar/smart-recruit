package com.smartrecruit.identity.application;

import com.smartrecruit.identity.domain.CommonPasswords;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

/** US-47 : mot de passe temporaire conforme à la politique de mots de passe. */
class TemporaryPasswordsTest {

    @RepeatedTest(20)
    void looksLikeFourGroupsWithoutAmbiguousCharacters() {
        String password = TemporaryPasswords.generate();

        assertThat(password).matches("[a-km-zA-HJ-NP-Z2-9]{4}(-[a-km-zA-HJ-NP-Z2-9]{4}){3}");
        assertThat(password).hasSizeGreaterThanOrEqualTo(12);
        assertThat(CommonPasswords.contains(password)).isFalse();
    }

    @Test
    void neverRepeats() {
        Set<String> generated = new HashSet<>();
        IntStream.range(0, 1000).forEach(i -> generated.add(TemporaryPasswords.generate()));
        assertThat(generated).hasSize(1000);
    }
}
