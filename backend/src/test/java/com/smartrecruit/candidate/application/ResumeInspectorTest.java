package com.smartrecruit.candidate.application;

import com.smartrecruit.candidate.domain.ResumeRules;
import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.support.TestPdfs;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** US-17, RG-07 : les quatre contrôles, dans l'ordre. */
class ResumeInspectorTest {

    private final ResumeInspector inspector = new ResumeInspector();

    @Test
    void validPdfIsAccepted() {
        assertThatCode(() -> inspector.inspect(file("application/pdf", TestPdfs.valid()))).doesNotThrowAnyException();
    }

    @Test
    void contentTypeParametersAreIgnored() {
        assertThatCode(() -> inspector.inspect(file("Application/PDF; charset=binary", TestPdfs.valid())))
                .doesNotThrowAnyException();
    }

    @Test
    void textFileRenamedAsPdfIsRefusedEvenWithThePdfContentType() {
        assertThatThrownBy(() -> inspector.inspect(file("application/pdf", TestPdfs.textRenamedAsPdf())))
                .satisfies(e -> assertCode(e, ErrorCode.FILE_INVALID_TYPE));
    }

    @Test
    void realPdfDeclaredAsAnotherTypeIsRefused() {
        assertThatThrownBy(() -> inspector.inspect(file("image/png", TestPdfs.valid())))
                .satisfies(e -> assertCode(e, ErrorCode.FILE_INVALID_TYPE));
    }

    @Test
    void fileAboveFiveMegabytesIsRefused() {
        byte[] big = Arrays.copyOf(TestPdfs.valid(), ResumeRules.MAX_SIZE_BYTES + 1);

        assertThatThrownBy(() -> inspector.inspect(file("application/pdf", big)))
                .satisfies(e -> assertCode(e, ErrorCode.FILE_TOO_LARGE));
    }

    @Test
    void emptyFileIsRefused() {
        assertThatThrownBy(() -> inspector.inspect(file("application/pdf", new byte[0])))
                .satisfies(e -> assertCode(e, ErrorCode.FILE_UNREADABLE));
    }

    @Test
    void passwordProtectedPdfIsRefused() {
        assertThatThrownBy(() -> inspector.inspect(file("application/pdf", TestPdfs.passwordProtected())))
                .satisfies(e -> assertCode(e, ErrorCode.FILE_UNREADABLE));
    }

    @Test
    void corruptedPdfIsRefused() {
        assertThatThrownBy(() -> inspector.inspect(file("application/pdf", TestPdfs.corrupted())))
                .satisfies(e -> assertCode(e, ErrorCode.FILE_UNREADABLE));
    }

    private static UploadedFile file(String contentType, byte[] content) {
        return new UploadedFile("cv.pdf", contentType, content);
    }

    private static void assertCode(Throwable e, ErrorCode expected) {
        org.assertj.core.api.Assertions.assertThat(e).isInstanceOf(BusinessRuleViolationException.class);
        org.assertj.core.api.Assertions.assertThat(((BusinessRuleViolationException) e).code()).isEqualTo(expected);
    }
}
