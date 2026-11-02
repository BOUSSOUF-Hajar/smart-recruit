package com.smartrecruit.shared.web;

import com.smartrecruit.shared.domain.BusinessRuleViolationException;
import com.smartrecruit.shared.domain.ErrorCode;
import com.smartrecruit.shared.domain.InvalidStateException;
import com.smartrecruit.shared.domain.ResourceNotFoundException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * US-03 : toutes les erreurs ont le même format, quelle que soit leur origine.
 * Tranche web seule (pas de base) : un contrôleur de test déclenche chaque type d'erreur.
 * <p>
 * La vraie configuration de sécurité n'est pas chargée par {@code @WebMvcTest} : une chaîne
 * ouverte la remplace, car ce test porte sur le format des erreurs, pas sur les droits (testés par
 * {@code SecurityMatrixIT}).
 */
@WebMvcTest(controllers = GlobalExceptionHandlerTest.ErrorTestController.class)   // sans les contrôleurs métier
@ActiveProfiles("test")
@Import({GlobalExceptionHandlerTest.ErrorTestController.class, GlobalExceptionHandlerTest.OpenSecurity.class})
@ExtendWith(OutputCaptureExtension.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void invalidFieldsGive400WithTheListOfFields() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\": \"\", \"email\": \"pas-un-email\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.type").value("https://smartrecruit.dev/errors/validation-failed"))
                .andExpect(jsonPath("$.instance").value("/test/validation"))
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[?(@.field == 'name')]").exists())
                .andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists());
    }

    @Test
    void malformedJsonGives400() throws Exception {
        mockMvc.perform(post("/test/validation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ pas du json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void invalidRequestParameterGives400() throws Exception {
        mockMvc.perform(get("/test/page").param("size", "500"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.errors[0].field").value("size"));
    }

    @Test
    void resourceNotFoundGives404() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.detail").value("Offre introuvable."));
    }

    @Test
    void unknownRouteGives404InTheSameFormat() throws Exception {
        mockMvc.perform(get("/route-qui-n-existe-pas"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentType(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void wrongHttpMethodGives405AndKeepsAllowHeader() throws Exception {
        mockMvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().exists("Allow"))
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void invalidStateGives409WithItsOwnCode() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"))
                .andExpect(jsonPath("$.title").value("Transition de statut interdite"));
    }

    @Test
    void businessRuleGives422WithDetailsAtTopLevel() throws Exception {
        mockMvc.perform(get("/test/business"))
                .andExpect(status().is(422))
                .andExpect(jsonPath("$.code").value("PROFILE_INCOMPLETE"))
                .andExpect(jsonPath("$.missing[0]").value("headline"))
                .andExpect(jsonPath("$.missing[1]").value("resume"));
    }

    @Test
    void optimisticLockGives409ConcurrentModification() throws Exception {
        mockMvc.perform(get("/test/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONCURRENT_MODIFICATION"));
    }

    @Test
    void unexpectedErrorGives500WithoutTechnicalDetail(CapturedOutput output) throws Exception {
        MvcResult result = mockMvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.detail").value(GlobalExceptionHandler.INTERNAL_ERROR_DETAIL))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("détail interne"))))
                .andExpect(content().string(not(containsString("trace"))))
                .andReturn();

        String correlationId = result.getResponse().getHeader(CorrelationIdFilter.HEADER);
        assertThat(correlationId).isNotBlank();
        assertThat(result.getResponse().getContentAsString()).contains(correlationId);
        // La trace complète est dans les logs, avec le même identifiant
        assertThat(output).contains(correlationId).contains("IllegalStateException");
    }

    @Test
    void correlationIdFromClientIsReusedAndReturned() throws Exception {
        mockMvc.perform(get("/test/not-found").header(CorrelationIdFilter.HEADER, "client-abc-12345"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, "client-abc-12345"))
                .andExpect(jsonPath("$.correlationId").value("client-abc-12345"));
    }

    @Test
    void malformedCorrelationIdIsReplaced() throws Exception {
        mockMvc.perform(get("/test/not-found").header(CorrelationIdFilter.HEADER, "bad id\n injected"))
                .andExpect(header().string(CorrelationIdFilter.HEADER, not(containsString("injected"))));
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class OpenSecurity {

        @Bean
        SecurityFilterChain openChain(HttpSecurity http) throws Exception {
            return http.csrf(csrf -> csrf.disable())
                    .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                    .build();
        }
    }

    /** Contrôleur présent uniquement dans les tests : chaque route déclenche une erreur précise. */
    @RestController
    @RequestMapping("/test")
    static class ErrorTestController {

        record Payload(@NotBlank String name, @Email String email) {
        }

        @PostMapping("/validation")
        void validation(@Valid @RequestBody Payload payload) {
        }

        @GetMapping("/page")
        void page(@RequestParam @Max(100) int size) {
        }

        @GetMapping("/not-found")
        void notFound() {
            throw new ResourceNotFoundException("Offre");
        }

        @GetMapping("/conflict")
        void conflict() {
            throw new InvalidStateException(ErrorCode.INVALID_STATUS_TRANSITION,
                    "Une candidature HIRED ne peut plus changer de statut.");
        }

        @GetMapping("/business")
        void business() {
            throw new BusinessRuleViolationException(ErrorCode.PROFILE_INCOMPLETE,
                    "Profil incomplet pour postuler.", Map.of("missing", List.of("headline", "resume")));
        }

        @GetMapping("/lock")
        void lock() {
            throw new OptimisticLockingFailureException("version obsolète");
        }

        @GetMapping("/boom")
        void boom() {
            throw new IllegalStateException("détail interne à ne jamais exposer");
        }
    }
}
