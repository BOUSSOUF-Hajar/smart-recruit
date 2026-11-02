package com.smartrecruit.identity.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** ENF-SEC-02 */
class CommonPasswordsTest {

    @Test
    void listIsLoadedWithoutCommentLines() {
        assertThat(CommonPasswords.size()).isGreaterThan(100);
        assertThat(CommonPasswords.contains("# liste")).isFalse();
    }

    @Test
    void comparisonIgnoresCase() {
        assertThat(CommonPasswords.contains("123456789012")).isTrue();
        assertThat(CommonPasswords.contains("QWERTY123456")).isTrue();
        assertThat(CommonPasswords.contains(" qwerty123456 ")).isTrue();
    }

    @Test
    void longPassphraseIsAccepted() {
        assertThat(CommonPasswords.contains("une-phrase-de-passe-longue")).isFalse();
        assertThat(CommonPasswords.contains(null)).isFalse();
    }
}
