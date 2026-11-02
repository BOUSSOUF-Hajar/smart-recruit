package com.smartrecruit.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Candidats prêts à postuler (profil minimal de RG-09 et CV actif) et candidatures, créés par l'API.
 * À importer avec {@code @Import(ApplicantFixtures.class)}.
 */
@TestComponent
public class ApplicantFixtures {

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;

    public ApplicantFixtures(MockMvc mockMvc, JdbcClient jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    /** Candidat neuf : titre, une compétence (Java), CV {@code cv-salma.pdf}. */
    public CandidateSession readyCandidate() throws Exception {
        return readyCandidate("Salma", "Idrissi", "java");
    }

    /** Candidat neuf avec ce nom et ces compétences (noms normalisés du référentiel). */
    public CandidateSession readyCandidate(String firstName, String lastName, String... skills) throws Exception {
        CandidateSession candidate = CandidateSession.register(mockMvc);
        profile(candidate, firstName, lastName, "Développeuse Java / Angular");
        skills(candidate, skills);
        uploadResume(candidate, "cv-salma.pdf");
        return candidate;
    }

    public void profile(CandidateSession candidate, String firstName, String lastName, String headline)
            throws Exception {
        String profile = mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/profile")))
                .andReturn().getResponse().getContentAsString();
        int version = JsonPath.read(profile, "$.version");
        mockMvc.perform(candidate.auth(put("/api/v1/candidates/me/profile"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"firstName":"%s","lastName":"%s","location":{"city":"Rabat","countryCode":"MA"},
                                 "headline":"%s","yearsOfExperience":4,"version":%d}""".formatted(firstName, lastName,
                                headline, version)))
                .andExpect(status().isOk());
    }

    public void skills(CandidateSession candidate, String... normalizedNames) throws Exception {
        StringBuilder entries = new StringBuilder();
        for (String name : normalizedNames) {
            entries.append(entries.isEmpty() ? "" : ",")
                    .append("{\"skillId\":\"%s\",\"level\":\"ADVANCED\",\"years\":3}".formatted(skillId(name)));
        }
        mockMvc.perform(candidate.auth(put("/api/v1/candidates/me/skills"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"skills\":[" + entries + "]}"))
                .andExpect(status().isOk());
    }

    public UUID uploadResume(CandidateSession candidate, String filename) throws Exception {
        return uploadResume(candidate, filename, TestPdfs.valid());
    }

    public UUID uploadResume(CandidateSession candidate, String filename, byte[] pdf) throws Exception {
        MvcResult result = mockMvc.perform(candidate.auth(multipart("/api/v1/candidates/me/resume")
                        .file(new MockMultipartFile("file", filename, "application/pdf", pdf))))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /** Candidature créée ; renvoie son identifiant. */
    public UUID apply(CandidateSession candidate, UUID jobId) throws Exception {
        MvcResult result = mockMvc.perform(candidate.auth(post("/api/v1/jobs/" + jobId + "/applications")))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    /** Fait avancer une candidature jusqu'à {@code target} en passant par chaque étape. */
    public void advance(StaffSessions.Session recruiter, UUID applicationId, String... targets) throws Exception {
        for (String target : targets) {
            mockMvc.perform(recruiter.auth(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
                            .patch("/api/v1/applications/" + applicationId + "/status"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"targetStatus\":\"%s\",\"version\":%d}".formatted(target, version(applicationId))))
                    .andExpect(status().isOk());
        }
    }

    public UUID skillId(String normalizedName) {
        return jdbc.sql("select id from skill where normalized_name = :name")
                .param("name", normalizedName).query(UUID.class).single();
    }

    public long version(UUID applicationId) {
        return jdbc.sql("select version from application where id = :id")
                .param("id", applicationId).query(Long.class).single();
    }
}
