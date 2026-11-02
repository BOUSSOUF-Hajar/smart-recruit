package com.smartrecruit.application;

import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.support.ApplicantFixtures;
import com.smartrecruit.support.CandidateSession;
import com.smartrecruit.support.JobFixtures;
import com.smartrecruit.support.QueryCounter;
import com.smartrecruit.support.StaffSessions;
import com.smartrecruit.support.StaffSessions.Session;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-30, US-28 : le nombre de requêtes SQL d'une page ne dépend pas du nombre de lignes (pas de N+1).
 * On compare une page de quelques lignes avec une page de 20 lignes.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class,
        QueryCounter.class})
class ApplicationListQueriesIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Autowired
    private JobFixtures jobs;

    @Autowired
    private ApplicantFixtures applicants;

    @Autowired
    private QueryCounter queries;

    @Test
    void listsUseAFixedNumberOfQueries() throws Exception {
        Session recruiter = staff.recruiterOfNewCompany(staff.admin());
        CandidateSession busyCandidate = applicants.readyCandidate();
        List<UUID> jobIds = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            jobIds.add(jobs.published(recruiter, "Offre " + i, "Casablanca", null));
        }
        for (int i = 0; i < 2; i++) {
            applicants.apply(busyCandidate, jobIds.get(i));
            applicants.apply(applicants.readyCandidate(), jobIds.get(0));
        }

        int recruiterSmall = count(recruiter.auth(get("/api/v1/applications")), 4);
        List<String> recruiterSmallSql = queries.statements();
        // Page pleine dans les deux cas : Spring Data ne compte pas le total d'une première page incomplète
        int candidateSmall = count(busyCandidate.auth(get("/api/v1/candidates/me/applications").param("size", "2")), 2);
        List<String> candidateSmallSql = queries.statements();

        for (int i = 2; i < 20; i++) {
            applicants.apply(busyCandidate, jobIds.get(i));
        }

        assertThat(count(recruiter.auth(get("/api/v1/applications")), 20))
                .as("liste recruteur : %s puis %s", recruiterSmallSql, queries.statements())
                .isEqualTo(recruiterSmall);
        assertThat(count(busyCandidate.auth(get("/api/v1/candidates/me/applications")), 20))
                .as("liste candidat : %s puis %s", candidateSmallSql, queries.statements())
                .isEqualTo(candidateSmall);
    }

    private int count(MockHttpServletRequestBuilder request, int expectedRows) throws Exception {
        queries.reset();
        mockMvc.perform(request).andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(expectedRows));
        return queries.count();
    }
}
