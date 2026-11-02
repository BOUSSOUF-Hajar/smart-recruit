package com.smartrecruit.candidate.domain;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullSource;

import static org.assertj.core.api.Assertions.assertThat;

/** US-17 : le nom d'origine n'est qu'une métadonnée nettoyée. */
class OriginalFilenameTest {

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            cv-salma.pdf                       | cv-salma.pdf
            ../../etc/passwd                   | passwd.pdf
            ..\\..\\Windows\\system.ini        | system.ini.pdf
            C:\\Users\\Salma\\CV Salma 2026.PDF | CV Salma 2026.PDF
            CV "final";.pdf                    | CV_final_.pdf
            .htaccess                          | htaccess.pdf
            Curriculum vitæ – Youssef.pdf      | Curriculum vitæ_Youssef.pdf
            ../                                | cv.pdf
            """)
    void keepsOnlyASafeFileName(String raw, String expected) {
        assertThat(OriginalFilename.sanitize(raw)).isEqualTo(expected);
    }

    @ParameterizedTest
    @NullSource
    void missingNameGetsADefault(String raw) {
        assertThat(OriginalFilename.sanitize(raw)).isEqualTo("cv.pdf");
    }

    @org.junit.jupiter.api.Test
    void veryLongNamesAreCutButKeepTheExtension() {
        String sanitized = OriginalFilename.sanitize("a".repeat(400) + ".pdf");

        assertThat(sanitized).hasSize(255).endsWith(".pdf");
    }
}
