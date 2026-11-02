package com.smartrecruit.referential;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.support.CandidateSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-15 : référentiel de compétences et catégories, avec les données de départ (V7). */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class ReferentialIT {

    @Autowired
    private MockMvc mockMvc;

    private CandidateSession candidate;

    @BeforeEach
    void setUp() throws Exception {
        candidate = CandidateSession.register(mockMvc);
    }

    @Test
    void categoriesArePublicAndSeeded() throws Exception {
        // L'ordre exact dépend de la collation de la base (accents) : seule la présence est vérifiée ici
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(10))))
                .andExpect(jsonPath("$[*].name", hasItem("Développement logiciel")))
                .andExpect(jsonPath("$[*].name", hasItem("Ressources humaines")));
    }

    @Test
    void typingSprSuggestsSpringBoot() throws Exception {
        mockMvc.perform(candidate.auth(get("/api/v1/skills").param("q", "spr")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Spring Boot")))
                .andExpect(jsonPath("$[*].name", hasItem("Spring Security")));
    }

    @Test
    void aliasesAreSearchedToo() throws Exception {
        mockMvc.perform(candidate.auth(get("/api/v1/skills").param("q", "k8")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name", hasItem("Kubernetes")));
    }

    @Test
    void resultsAreCappedAtTwenty() throws Exception {
        mockMvc.perform(candidate.auth(get("/api/v1/skills").param("q", "").param("size", "500")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(lessThanOrEqualTo(20))));
    }

    @Test
    void searchRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/v1/skills").param("q", "spr")).andExpect(status().isUnauthorized());
    }

    @Test
    void newSkillIsCreatedOnceInNormalizedForm() throws Exception {
        String unique = "Quarkus " + UUID.randomUUID().toString().substring(0, 6);

        String created = mockMvc.perform(candidate.auth(post("/api/v1/skills"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"  %s  \"}".formatted(unique)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value(unique))
                .andReturn().getResponse().getContentAsString();

        // Même compétence, autre casse et espaces : renvoyée telle quelle, sans doublon
        mockMvc.perform(candidate.auth(post("/api/v1/skills"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(unique.toUpperCase().replace(" ", "   "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((String) JsonPath.read(created, "$.id")));
    }

    @Test
    void existingAliasReturnsTheCanonicalSkill() throws Exception {
        mockMvc.perform(candidate.auth(post("/api/v1/skills"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SpringBoot\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Spring Boot"));
    }

    @Test
    void invalidNamesAreRefused() throws Exception {
        mockMvc.perform(candidate.auth(post("/api/v1/skills"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"<script>\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"));
    }
}
