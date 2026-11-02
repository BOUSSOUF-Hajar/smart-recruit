package com.smartrecruit.candidate;

import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.candidate.domain.ResumeRules;
import com.smartrecruit.shared.storage.StorageProperties;
import com.smartrecruit.support.CandidateSession;
import com.smartrecruit.support.TestPdfs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.file.Files;
import java.util.Arrays;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-17 et US-18 : téléversement et téléchargement du CV, base et disque réels. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
@ExtendWith(OutputCaptureExtension.class)
class ResumeIT {

    private static final String RESUME = "/api/v1/candidates/me/resume";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcClient jdbc;

    @Autowired
    private StorageProperties storage;

    private CandidateSession candidate;

    @BeforeEach
    void setUp() throws Exception {
        candidate = CandidateSession.register(mockMvc);
    }

    @Test
    void validPdfBecomesTheActiveResume() throws Exception {
        byte[] pdf = TestPdfs.valid();

        upload("cv-salma.pdf", "application/pdf", pdf)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("cv-salma.pdf"))
                .andExpect(jsonPath("$.sizeBytes").value(pdf.length))
                .andExpect(jsonPath("$.uploadedAt").isNotEmpty())
                .andExpect(jsonPath("$.storageKey").doesNotExist());

        mockMvc.perform(candidate.auth(get(RESUME)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalFilename").value("cv-salma.pdf"));
        mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/profile")))
                .andExpect(jsonPath("$.resume.originalFilename").value("cv-salma.pdf"))
                .andExpect(jsonPath("$.completeness.missing", org.hamcrest.Matchers.not(
                        org.hamcrest.Matchers.hasItem("resume"))));
    }

    @Test
    void newResumeReplacesTheOldOneWhichIsKept() throws Exception {
        upload("cv-2025.pdf", "application/pdf", TestPdfs.valid()).andExpect(status().isCreated());
        upload("cv-2026.pdf", "application/pdf", TestPdfs.valid()).andExpect(status().isCreated());

        Map<String, Object> counts = jdbc.sql("""
                        select count(*) as total, count(*) filter (where active) as active
                        from resume where candidate_id = :id""")
                .param("id", candidate.userId()).query().singleRow();
        assertThat(counts).containsEntry("total", 2L).containsEntry("active", 1L);
        mockMvc.perform(candidate.auth(get(RESUME))).andExpect(jsonPath("$.originalFilename").value("cv-2026.pdf"));
    }

    @Test
    void textFileRenamedAsPdfIsRefusedAndNothingIsStored() throws Exception {
        upload("cv.pdf", "application/pdf", TestPdfs.textRenamedAsPdf())
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("FILE_INVALID_TYPE"));

        assertThat(resumeCount()).isZero();
    }

    @Test
    void fileAboveFiveMegabytesIsRefused() throws Exception {
        byte[] tooBig = Arrays.copyOf(TestPdfs.valid(), ResumeRules.MAX_SIZE_BYTES + 1);

        upload("cv.pdf", "application/pdf", tooBig)
                .andExpect(status().is(413))
                .andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void passwordProtectedPdfIsRefused() throws Exception {
        upload("cv.pdf", "application/pdf", TestPdfs.passwordProtected())
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("FILE_UNREADABLE"));
    }

    @Test
    void pathInTheFileNameIsDroppedAndTheFileGetsAUuidName() throws Exception {
        upload("../../etc/cv.pdf", "application/pdf", TestPdfs.valid())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.originalFilename").value("cv.pdf"));

        String key = storageKey();
        assertThat(key).matches("[0-9a-f-]{36}\\.pdf");
        assertThat(storage.rootDirectory().resolve(key)).exists();
    }

    @Test
    void downloadIsForcedAndAuthenticated() throws Exception {
        byte[] pdf = TestPdfs.valid();
        upload("CV Salma été.pdf", "application/pdf", pdf).andExpect(status().isCreated());

        mockMvc.perform(candidate.auth(get(RESUME + "/file")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, "application/pdf"))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("attachment")))
                .andExpect(header().string(HttpHeaders.CONTENT_DISPOSITION, containsString("filename*=UTF-8''")))
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, containsString("no-store")))
                .andExpect(content().bytes(pdf));

        mockMvc.perform(get(RESUME + "/file")).andExpect(status().isUnauthorized());
    }

    @Test
    void noResumeYetGives404() throws Exception {
        mockMvc.perform(candidate.auth(get(RESUME))).andExpect(status().isNotFound());
        mockMvc.perform(candidate.auth(get(RESUME + "/file"))).andExpect(status().isNotFound());
    }

    @Test
    void fileMissingOnDiskGives404AndAnAlertWithoutPath(CapturedOutput output) throws Exception {
        upload("cv.pdf", "application/pdf", TestPdfs.valid()).andExpect(status().isCreated());
        Files.delete(storage.rootDirectory().resolve(storageKey()));

        mockMvc.perform(candidate.auth(get(RESUME + "/file")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("CV introuvable."));

        assertThat(output).contains("absent du stockage");
        assertThat(output).doesNotContain(storage.rootDirectory().toAbsolutePath().toString());
    }

    @Test
    void missingFilePartIsAValidationError() throws Exception {
        mockMvc.perform(candidate.auth(multipart(RESUME)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    private ResultActions upload(String filename, String contentType, byte[] content) throws Exception {
        return mockMvc.perform(candidate.auth(multipart(RESUME)
                .file(new MockMultipartFile("file", filename, contentType, content))));
    }

    private long resumeCount() {
        return jdbc.sql("select count(*) from resume where candidate_id = :id")
                .param("id", candidate.userId()).query(Long.class).single();
    }

    private String storageKey() {
        return jdbc.sql("select storage_key from resume where candidate_id = :id and active")
                .param("id", candidate.userId()).query(String.class).single();
    }
}
