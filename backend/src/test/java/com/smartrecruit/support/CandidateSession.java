package com.smartrecruit.support;

import com.jayway.jsonpath.JsonPath;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.AbstractMockHttpServletRequestBuilder;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Candidat inscrit par l'API, avec son access token, pour les tests d'intégration des phases 4 et suivantes.
 * Chaque appel crée un compte neuf (email unique, IP aléatoire pour ne pas atteindre la limite d'inscriptions).
 */
public record CandidateSession(UUID userId, String email, String accessToken) {

    public static final String PASSWORD = "une-phrase-de-passe-longue";

    public static CandidateSession register(MockMvc mockMvc) throws Exception {
        String email = "candidat-" + UUID.randomUUID().toString().substring(0, 12) + "@example.com";
        String ip = "203.0.113." + ThreadLocalRandom.current().nextInt(1, 255);
        MvcResult result = mockMvc.perform(post("/api/v1/auth/register")
                        .with(request -> {
                            request.setRemoteAddr(ip);
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","firstName":"Salma","lastName":"Idrissi",
                                 "privacyAccepted":true}""".formatted(email, PASSWORD)))
                .andExpect(status().isCreated())
                .andReturn();
        String body = result.getResponse().getContentAsString();
        return new CandidateSession(UUID.fromString(JsonPath.read(body, "$.user.id")), email,
                JsonPath.read(body, "$.accessToken"));
    }

    /** Ajoute l'en-tête Authorization à une requête construite par le test. */
    public <T extends AbstractMockHttpServletRequestBuilder<T>> T auth(T request) {
        request.header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken);
        return request;
    }
}
