package com.smartrecruit.application;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.support.ApplicantFixtures;
import com.smartrecruit.support.CandidateSession;
import com.smartrecruit.support.JobFixtures;
import com.smartrecruit.support.StaffSessions;
import com.smartrecruit.support.StaffSessions.Session;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-39 : notes internes partagées entre recruteurs, jamais visibles du candidat. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class})
class NoteIT {

    private static final String SECRET = "Prétentions salariales trop élevées pour le budget";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Autowired
    private JobFixtures jobs;

    @Autowired
    private ApplicantFixtures applicants;

    private Session admin;
    private Session recruiter;
    private CandidateSession candidate;
    private UUID applicationId;
    private String notes;

    @BeforeEach
    void setUp() throws Exception {
        admin = staff.admin();
        recruiter = staff.recruiterOfNewCompany(admin);
        candidate = applicants.readyCandidate();
        applicationId = applicants.apply(candidate, jobs.published(recruiter, "Développeur Java", "Rabat", null));
        notes = "/api/v1/applications/" + applicationId + "/notes";
    }

    @Test
    void noteIsSharedWithColleaguesButOnlyItsAuthorChangesIt() throws Exception {
        Session colleague = staff.recruiter(admin, recruiter.companyId());
        String noteId = JsonPath.read(add(recruiter, SECRET)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.author.name").value("Youssef Alami"))
                .andExpect(jsonPath("$.edited").value(false))
                .andExpect(jsonPath("$.mine").value(true))
                .andReturn().getResponse().getContentAsString(), "$.id");

        mockMvc.perform(colleague.auth(get(notes)))
                .andExpect(jsonPath("$[0].content").value(SECRET))
                .andExpect(jsonPath("$[0].mine").value(false));
        mockMvc.perform(colleague.auth(put(notes + "/" + noteId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"Je réécris ta note\"}"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
        mockMvc.perform(colleague.auth(delete(notes + "/" + noteId))).andExpect(status().isForbidden());

        mockMvc.perform(recruiter.auth(put(notes + "/" + noteId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"" + SECRET + ", à rediscuter\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.edited").value(true));
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId)))
                .andExpect(jsonPath("$.notesCount").value(1));
        mockMvc.perform(recruiter.auth(get("/api/v1/applications/" + applicationId + "/history")))
                .andExpect(jsonPath("$[0].type").value("NOTE_ADDED"));

        mockMvc.perform(recruiter.auth(delete(notes + "/" + noteId))).andExpect(status().isNoContent());
        mockMvc.perform(recruiter.auth(get(notes))).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(recruiter.auth(put(notes + "/" + noteId)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"content\":\"retour\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void notesStayInsideTheCompanyAndAreNeverShownToTheCandidate() throws Exception {
        add(recruiter, SECRET).andExpect(status().isCreated());
        add(recruiter, "   ").andExpect(status().isBadRequest());
        Session outsider = staff.recruiterOfNewCompany(admin);

        mockMvc.perform(outsider.auth(get(notes))).andExpect(status().isNotFound());
        add(outsider, "intrusion").andExpect(status().isNotFound());
        mockMvc.perform(candidate.auth(get(notes))).andExpect(status().isForbidden());

        String detail = mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/applications/" + applicationId)))
                .andReturn().getResponse().getContentAsString();
        String list = mockMvc.perform(candidate.auth(get("/api/v1/candidates/me/applications")))
                .andReturn().getResponse().getContentAsString();
        for (String raw : List.of(detail, list)) {
            assertThat(raw).doesNotContain(SECRET, "note", "Note");
        }
    }

    private ResultActions add(Session who, String content) throws Exception {
        return mockMvc.perform(who.auth(post(notes)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"content\":\"" + content + "\"}"));
    }
}
