package com.smartrecruit.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.boot.test.context.TestComponent;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Offres de test créées par l'API, avec des valeurs par défaut réalistes. */
@TestComponent
public class JobFixtures {

    public static final String DESCRIPTION = "Nous recherchons un développeur pour concevoir des API Spring Boot "
            + "et des interfaces Angular au sein d'une équipe produit.";

    private final MockMvc mockMvc;
    private final JdbcClient jdbc;

    public JobFixtures(MockMvc mockMvc, JdbcClient jdbc) {
        this.mockMvc = mockMvc;
        this.jdbc = jdbc;
    }

    public UUID categoryId() {
        return jdbc.sql("select id from job_category where name = 'Développement logiciel'")
                .query(UUID.class).single();
    }

    public UUID skillId(String normalizedName) {
        return jdbc.sql("select id from skill where normalized_name = :name")
                .param("name", normalizedName).query(UUID.class).single();
    }

    /** Corps JSON complet d'une offre ; {@code overrides} est inséré à la fin de l'objet (remplace les champs). */
    public String json(String title, String city, String overrides) {
        return """
                {"title":"%s","description":"%s","categoryId":"%s",
                 "location":{"city":"%s","countryCode":"MA"},"workMode":"HYBRID","contractType":"CDI",
                 "experienceLevel":"MID","minYearsExperience":3,
                 "salary":{"min":15000,"max":22000,"currency":"MAD","period":"MONTHLY","visible":true},
                 "requiredSkills":[{"skillId":"%s","minLevel":"ADVANCED"}],
                 "preferredSkills":[{"skillId":"%s"}]%s}"""
                .formatted(title, DESCRIPTION, categoryId(), city, skillId("java"), skillId("angular"),
                        overrides == null ? "" : "," + overrides);
    }

    public UUID draft(StaffSessions.Session recruiter, String title, String city, String overrides) throws Exception {
        MvcResult result = mockMvc.perform(recruiter.auth(post("/api/v1/jobs"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(title, city, overrides)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    public UUID published(StaffSessions.Session recruiter, String title, String city, String overrides)
            throws Exception {
        UUID id = draft(recruiter, title, city, overrides);
        mockMvc.perform(recruiter.auth(post("/api/v1/jobs/" + id + "/publish"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"expiryDate\":\"%s\"}".formatted(LocalDate.now().plusDays(30))))
                .andExpect(status().isOk());
        return id;
    }
}
