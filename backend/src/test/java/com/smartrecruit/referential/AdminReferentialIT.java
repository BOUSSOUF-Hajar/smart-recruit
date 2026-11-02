package com.smartrecruit.referential;

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
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** US-50 (catégories) et US-51 (fusion de compétences), sur un vrai PostgreSQL. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import({TestcontainersConfiguration.class, StaffSessions.class, JobFixtures.class, ApplicantFixtures.class})
class AdminReferentialIT {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private StaffSessions staff;

    @Autowired
    private JobFixtures jobs;

    @Autowired
    private ApplicantFixtures applicants;

    @Autowired
    private JdbcClient jdbc;

    private Session admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = staff.admin();
    }

    @Test
    void aNewCategoryIsOfferedInTheJobFormAndNamesStayUnique() throws Exception {
        String name = "Cybersécurité " + StaffSessions.suffix();
        UUID id = createCategory(name);

        mockMvc.perform(get("/api/v1/categories")).andExpect(jsonPath("$[*].name", hasItem(name)));
        mockMvc.perform(admin.auth(post("/api/v1/admin/categories")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(name.toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"));

        String renamed = "Sécurité informatique " + StaffSessions.suffix();
        mockMvc.perform(admin.auth(put("/api/v1/admin/categories/{id}", id)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"%s\"}".formatted(renamed)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(renamed));
        assertThat(audited("CATEGORY_CREATED", id)).isEqualTo(1);
        assertThat(audited("CATEGORY_RENAMED", id)).isEqualTo(1);
    }

    @Test
    void aUsedCategoryCannotBeDeletedOnlyDeactivated() throws Exception {
        UUID categoryId = createCategory("Data " + StaffSessions.suffix());
        Session recruiter = staff.recruiterOfNewCompany(admin);
        jobs.draft(recruiter, "Data engineer", "Rabat", "\"categoryId\":\"%s\"".formatted(categoryId));

        mockMvc.perform(admin.auth(delete("/api/v1/admin/categories/{id}", categoryId)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.usageCount").value(1));

        mockMvc.perform(admin.auth(post("/api/v1/admin/categories/{id}/deactivate", categoryId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false))
                .andExpect(jsonPath("$.offerCount").value(1));
        // Plus proposée : ni dans la liste publique, ni pour une nouvelle offre
        mockMvc.perform(get("/api/v1/categories"))
                .andExpect(jsonPath("$[*].id", not(hasItem(categoryId.toString()))));
        mockMvc.perform(recruiter.auth(post("/api/v1/jobs")).contentType(MediaType.APPLICATION_JSON)
                        .content(jobs.json("Autre", "Rabat", "\"categoryId\":\"%s\"".formatted(categoryId))))
                .andExpect(status().isBadRequest());
        // L'admin la voit toujours, avec son nombre d'offres
        mockMvc.perform(admin.auth(get("/api/v1/admin/categories")))
                .andExpect(jsonPath("$[?(@.id == '%s')].offerCount".formatted(categoryId)).value(1));
    }

    @Test
    void anUnusedCategoryIsDeletedAndAudited() throws Exception {
        UUID id = createCategory("Éphémère " + StaffSessions.suffix());

        mockMvc.perform(admin.auth(delete("/api/v1/admin/categories/{id}", id))).andExpect(status().isNoContent());

        assertThat(jdbc.sql("select count(*) from job_category where id = :id").param("id", id)
                .query(Integer.class).single()).isZero();
        assertThat(audited("CATEGORY_DELETED", id)).isEqualTo(1);
    }

    /**
     * Critères de US-51 : profils et offres reportés, un seul exemplaire au niveau le plus élevé,
     * l'ancien nom devient un alias, fusion auditée.
     */
    @Test
    void mergingMovesProfilesAndJobsKeepsTheHighestLevelAndLeavesAnAlias() throws Exception {
        String suffix = StaffSessions.suffix();
        CandidateSession creator = applicants.readyCandidate();
        UUID source = createSkill(creator, "springboot" + suffix);
        UUID target = createSkill(creator, "Spring Boot " + suffix);

        CandidateSession both = applicants.readyCandidate();
        setSkills(both, Map.of(source, "EXPERT", target, "BEGINNER"));
        CandidateSession onlySource = applicants.readyCandidate();
        setSkills(onlySource, Map.of(source, "INTERMEDIATE"));
        Session recruiter = staff.recruiterOfNewCompany(admin);
        UUID job = jobs.draft(recruiter, "Développeur Spring", "Rabat", """
                "requiredSkills":[{"skillId":"%s","minLevel":"ADVANCED"}],
                "preferredSkills":[{"skillId":"%s"}]""".formatted(source, target));

        mockMvc.perform(admin.auth(get("/api/v1/admin/skills")).param("q", "springboot" + suffix))
                .andExpect(jsonPath("$.content[0].id").value(source.toString()))
                .andExpect(jsonPath("$.content[0].usageCount").value(3));

        mockMvc.perform(admin.auth(post("/api/v1/admin/skills/{id}/merge", source))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetSkillId\":\"%s\"}".formatted(target)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target.id").value(target.toString()))
                .andExpect(jsonPath("$.target.usageCount").value(3))
                .andExpect(jsonPath("$.target.aliases", hasItem("springboot" + suffix)))
                .andExpect(jsonPath("$.references").value(3));

        // Le profil qui avait les deux n'en garde qu'une, au niveau le plus élevé
        assertThat(jdbc.sql("select skill_id::text || ':' || level from candidate_skill where candidate_id = :id")
                .param("id", both.userId()).query(String.class).list()).containsExactly(target + ":EXPERT");
        assertThat(jdbc.sql("select level from candidate_skill where candidate_id = :id and skill_id = :skill")
                .param("id", onlySource.userId()).param("skill", target).query(String.class).single())
                .isEqualTo("INTERMEDIATE");
        // L'offre garde la cible, requise (l'exigence la plus forte), au niveau minimal ADVANCED
        assertThat(jdbc.sql("""
                        select requirement || ':' || coalesce(min_level, '') from job_offer_skill
                        where job_offer_id = :job""")
                .param("job", job).query(String.class).list()).containsExactly("REQUIRED:ADVANCED");
        // La source a disparu ; son nom désigne désormais la cible
        assertThat(jdbc.sql("select count(*) from skill where id = :id").param("id", source)
                .query(Integer.class).single()).isZero();
        mockMvc.perform(creator.auth(post("/api/v1/skills")).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"SpringBoot%s\"}".formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.toString()));
        assertThat(jdbc.sql("""
                        select details->>'sourceName' from audit_log
                        where action = 'SKILL_MERGED' and resource_id = :target""")
                .param("target", target).query(String.class).single()).isEqualTo("springboot" + suffix);
    }

    @Test
    void mergeAndRenameGuardAgainstMistakes() throws Exception {
        String suffix = StaffSessions.suffix();
        CandidateSession creator = applicants.readyCandidate();
        UUID skill = createSkill(creator, "Kotlinn " + suffix);
        UUID other = createSkill(creator, "Scala " + suffix);

        mockMvc.perform(admin.auth(post("/api/v1/admin/skills/{id}/merge", skill))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetSkillId\":\"%s\"}".formatted(skill)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("targetSkillId"));
        // Un nom déjà pris appelle une fusion
        mockMvc.perform(admin.auth(put("/api/v1/admin/skills/{id}", skill)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"scala %s\"}".formatted(suffix)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESOURCE_IN_USE"))
                .andExpect(jsonPath("$.skillId").value(other.toString()));
        // Corriger une faute : l'ancienne orthographe devient un alias
        mockMvc.perform(admin.auth(put("/api/v1/admin/skills/{id}", skill)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Kotlin %s\"}".formatted(suffix)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kotlin " + suffix))
                .andExpect(jsonPath("$.aliases", hasItem("kotlinn " + suffix)));
        // Une cible désactivée est refusée
        mockMvc.perform(admin.auth(post("/api/v1/admin/skills/{id}/deactivate", other)))
                .andExpect(jsonPath("$.active").value(false));
        mockMvc.perform(admin.auth(post("/api/v1/admin/skills/{id}/merge", skill))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"targetSkillId\":\"%s\"}".formatted(other)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));
    }

    private UUID createCategory(String name) throws Exception {
        MvcResult result = mockMvc.perform(admin.auth(post("/api/v1/admin/categories"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private UUID createSkill(CandidateSession creator, String name) throws Exception {
        MvcResult result = mockMvc.perform(creator.auth(post("/api/v1/skills"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"name\":\"%s\"}".formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return UUID.fromString(JsonPath.read(result.getResponse().getContentAsString(), "$.id"));
    }

    private void setSkills(CandidateSession candidate, Map<UUID, String> levels) throws Exception {
        String entries = String.join(",", levels.entrySet().stream()
                .map(entry -> "{\"skillId\":\"%s\",\"level\":\"%s\",\"years\":2}".formatted(entry.getKey(),
                        entry.getValue()))
                .toList());
        mockMvc.perform(candidate.auth(put("/api/v1/candidates/me/skills"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"skills\":[" + entries + "]}"))
                .andExpect(status().isOk());
    }

    private int audited(String action, UUID resourceId) {
        return jdbc.sql("select count(*) from audit_log where action = :action and resource_id = :id")
                .param("action", action).param("id", resourceId).query(Integer.class).single();
    }
}
