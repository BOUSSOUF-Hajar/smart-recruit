package com.smartrecruit.candidate;

import com.jayway.jsonpath.JsonPath;
import com.smartrecruit.TestcontainersConfiguration;
import com.smartrecruit.support.CandidateSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-13 à US-16 et US-20 de bout en bout, sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestcontainersConfiguration.class)
class CandidateProfileIT {

    private static final String ME = "/api/v1/candidates/me";

    @Autowired
    private MockMvc mockMvc;

    private CandidateSession candidate;

    @BeforeEach
    void setUp() throws Exception {
        candidate = CandidateSession.register(mockMvc);
    }

    // ---------------------------------------------------------------- US-13 informations de base

    @Test
    void newCandidateStartsWithAnEmptyProfile() throws Exception {
        mockMvc.perform(candidate.auth(get(ME + "/profile")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Salma"))
                .andExpect(jsonPath("$.email").value(candidate.email()))
                .andExpect(jsonPath("$.experiences", hasSize(0)))
                .andExpect(jsonPath("$.resume").doesNotExist())
                .andExpect(jsonPath("$.completeness.percent").value(10))
                .andExpect(jsonPath("$.version").value(0));
    }

    @Test
    void basicInformationIsSavedOnTheProfileAndTheAccount() throws Exception {
        updateBasics(0, "+212600000000", "x".repeat(50))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.firstName").value("Salma-Nour"))
                .andExpect(jsonPath("$.phone").value("+212600000000"))
                .andExpect(jsonPath("$.location.city").value("Marrakech"))
                .andExpect(jsonPath("$.location.countryCode").value("MA"))
                .andExpect(jsonPath("$.headline").value("Développeuse Java / Angular"))
                .andExpect(jsonPath("$.yearsOfExperience").value(4))
                .andExpect(jsonPath("$.version").value(1))
                .andExpect(jsonPath("$.completeness.percent").value(35));

        // Réaffiché tel quel, et le prénom est aussi celui du compte
        mockMvc.perform(candidate.auth(get(ME + "/profile")))
                .andExpect(jsonPath("$.headline").value("Développeuse Java / Angular"));
        mockMvc.perform(candidate.auth(get("/api/v1/me")))
                .andExpect(jsonPath("$.firstName").value("Salma-Nour"));
    }

    @Test
    void staleVersionIsRefusedInsteadOfOverwriting() throws Exception {
        updateBasics(0, null, "Premier résumé").andExpect(status().isOk());

        updateBasics(0, null, "Résumé écrit dans un autre onglet")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void invalidPhoneAndTooLongSummaryAreFieldErrors() throws Exception {
        updateBasics(0, "0600000000", "x".repeat(2001))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[?(@.field == 'phone')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'summary')]").exists());
    }

    // ---------------------------------------------------------------- US-14 expériences et formations

    @Test
    void experiencesAreListedMostRecentFirstWithCurrentOnTop() throws Exception {
        addExperience("Développeuse junior", "2019-09-01", "2022-02-28").andExpect(status().isCreated());
        addExperience("Développeuse", "2022-03-01", null)
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.LOCATION, startsWith(ME + "/experiences/")))
                .andExpect(jsonPath("$.current").value(true));
        addExperience("Stagiaire", "2019-02-01", "2019-07-31").andExpect(status().isCreated());

        mockMvc.perform(candidate.auth(get(ME + "/profile")))
                .andExpect(jsonPath("$.experiences[*].jobTitle")
                        .value(org.hamcrest.Matchers.contains("Développeuse", "Développeuse junior", "Stagiaire")))
                .andExpect(jsonPath("$.experiences[0].endDate").doesNotExist());
    }

    @Test
    void experienceCanBeUpdatedAndDeleted() throws Exception {
        String id = idOf(addExperience("Développeuse", "2022-03-01", null));

        mockMvc.perform(candidate.auth(put(ME + "/experiences/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(experienceJson("Lead développeuse", "2022-03-01", "2025-12-31")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Lead développeuse"))
                .andExpect(jsonPath("$.current").value(false));

        mockMvc.perform(candidate.auth(delete(ME + "/experiences/" + id))).andExpect(status().isNoContent());
        mockMvc.perform(candidate.auth(get(ME + "/profile"))).andExpect(jsonPath("$.experiences", hasSize(0)));
    }

    @Test
    void endBeforeStartIsRefusedOnTheEndDate() throws Exception {
        addExperience("Développeuse", "2022-03-01", "2021-01-01")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endDate"));

        mockMvc.perform(candidate.auth(post(ME + "/educations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"degree":"Master","school":"ENSIAS","startYear":2022,"endYear":2020}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("endYear"));
    }

    @Test
    void anotherCandidateCannotTouchMyExperience() throws Exception {
        String id = idOf(addExperience("Développeuse", "2022-03-01", null));
        CandidateSession intruder = CandidateSession.register(mockMvc);

        mockMvc.perform(intruder.auth(put(ME + "/experiences/" + id))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(experienceJson("Piratée", "2022-03-01", null)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        mockMvc.perform(intruder.auth(delete(ME + "/experiences/" + id))).andExpect(status().isNotFound());

        mockMvc.perform(candidate.auth(get(ME + "/profile")))
                .andExpect(jsonPath("$.experiences[0].jobTitle").value("Développeuse"));
    }

    @Test
    void educationIsAdded() throws Exception {
        mockMvc.perform(candidate.auth(post(ME + "/educations"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"degree":"Diplôme d'ingénieur d'État","school":"ENSIAS",
                                 "fieldOfStudy":"Génie logiciel","startYear":2017,"endYear":2022}"""))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startYear").value(2017));
    }

    // ---------------------------------------------------------------- US-15 compétences

    @Test
    void skillsFromTheReferentialAreSavedWithLevelAndYears() throws Exception {
        String springBoot = skillId("spring boot");
        String java = skillId("java");

        replaceSkills("""
                [{"skillId":"%s","level":"ADVANCED","years":3},{"skillId":"%s","level":"EXPERT"}]"""
                .formatted(springBoot, java))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.skills", hasSize(2)))
                .andExpect(jsonPath("$.skills[0].name").value("Java"))
                .andExpect(jsonPath("$.skills[0].years").doesNotExist())
                .andExpect(jsonPath("$.skills[1].name").value("Spring Boot"))
                .andExpect(jsonPath("$.skills[1].level").value("ADVANCED"));
    }

    @Test
    void duplicateUnknownOrTooManySkillsAreRefused() throws Exception {
        String java = skillId("java");

        replaceSkills("""
                [{"skillId":"%s","level":"ADVANCED"},{"skillId":"%s","level":"EXPERT"}]""".formatted(java, java))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skills[1].skillId"));

        replaceSkills("""
                [{"skillId":"%s","level":"ADVANCED"}]""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skills[0].skillId"));

        String fiftyOne = IntStream.range(0, 51)
                .mapToObj(i -> "{\"skillId\":\"%s\",\"level\":\"BEGINNER\"}".formatted(UUID.randomUUID()))
                .collect(Collectors.joining(",", "[", "]"));
        replaceSkills(fiftyOne)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("skills"));
    }

    // ---------------------------------------------------------------- US-16 langues et liens

    @Test
    void languagesAreUniqueAndLevelled() throws Exception {
        mockMvc.perform(candidate.auth(put(ME + "/languages"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"languages":[{"languageCode":"fr","level":"C1"},{"languageCode":"ar","level":"NATIVE"}]}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.languages[*].languageCode")
                        .value(org.hamcrest.Matchers.contains("ar", "fr")));

        mockMvc.perform(candidate.auth(put(ME + "/languages"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"languages":[{"languageCode":"fr","level":"C1"},{"languageCode":"fr","level":"B2"}]}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("languages[1].languageCode"));
    }

    @Test
    void linksMustBeHttpsAndAtMostFive() throws Exception {
        replaceLinks("""
                [{"type":"GITHUB","url":"http://github.com/salma"}]""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("links[0].url"));

        String six = IntStream.range(0, 6)
                .mapToObj(i -> "{\"type\":\"OTHER\",\"url\":\"https://example.com/%d\"}".formatted(i))
                .collect(Collectors.joining(",", "[", "]"));
        replaceLinks(six)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("links"));

        replaceLinks("""
                [{"type":"LINKEDIN","url":"https://www.linkedin.com/in/salma"}]""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.links[0].type").value("LINKEDIN"));
    }

    // ---------------------------------------------------------------- US-20 complétude

    @Test
    void completenessFollowsTheProfile() throws Exception {
        updateBasics(0, null, "Résumé").andExpect(jsonPath("$.completeness.percent").value(35));
        addExperience("Développeuse", "2022-03-01", null);

        mockMvc.perform(candidate.auth(get(ME + "/profile")))
                .andExpect(jsonPath("$.completeness.percent").value(55))
                .andExpect(jsonPath("$.completeness.missing")
                        .value(org.hamcrest.Matchers.contains("education", "skills", "language", "resume")));
    }

    // ---------------------------------------------------------------- outils

    private ResultActions updateBasics(long version, String phone, String summary) throws Exception {
        String phoneJson = phone == null ? "null" : "\"" + phone + "\"";
        return mockMvc.perform(candidate.auth(put(ME + "/profile"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"firstName":"Salma-Nour","lastName":"Idrissi","phone":%s,
                         "location":{"city":"Marrakech","countryCode":"MA"},
                         "headline":"Développeuse Java / Angular","summary":"%s","yearsOfExperience":4,
                         "version":%d}""".formatted(phoneJson, summary, version)));
    }

    private ResultActions addExperience(String title, String start, String end) throws Exception {
        return mockMvc.perform(candidate.auth(post(ME + "/experiences"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(experienceJson(title, start, end)));
    }

    private static String experienceJson(String title, String start, String end) {
        return """
                {"jobTitle":"%s","companyName":"Agence X","city":"Marrakech","startDate":"%s","endDate":%s,
                 "description":"Applications Spring Boot et Angular."}"""
                .formatted(title, start, end == null ? "null" : "\"" + end + "\"");
    }

    private ResultActions replaceSkills(String skillsArray) throws Exception {
        return mockMvc.perform(candidate.auth(put(ME + "/skills"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"skills\":" + skillsArray + "}"));
    }

    private ResultActions replaceLinks(String linksArray) throws Exception {
        return mockMvc.perform(candidate.auth(put(ME + "/links"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"links\":" + linksArray + "}"));
    }

    private String skillId(String query) throws Exception {
        String body = mockMvc.perform(candidate.auth(get("/api/v1/skills").param("q", query)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$[0].id");
    }

    private static String idOf(ResultActions created) throws Exception {
        return JsonPath.read(created.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
