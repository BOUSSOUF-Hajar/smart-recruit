package com.smartrecruit.ai.infrastructure.ollama;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.Status;
import org.springframework.web.client.RestClient;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/** US-40 : Ollama arrêté ne rend pas l'application DOWN ; le composant est signalé indisponible. */
class OllamaHealthIndicatorTest {

    @Test
    void stoppedOllamaIsReportedUnavailableWithoutBeingDown() {
        Health health = new OllamaHealthIndicator(RestClient.create("http://127.0.0.1:1"), "qwen-test:7b").health();

        assertThat(health.getStatus()).isEqualTo(Status.UNKNOWN);
        assertThat(health.getDetails()).containsEntry("available", false).containsEntry("reason", "Ollama injoignable");
    }

    @Test
    void installedModelIsUpAndMissingModelIsReported() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/tags", exchange -> {
            byte[] body = "{\"models\":[{\"name\":\"qwen-test:7b\"}]}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            RestClient client = RestClient.create("http://127.0.0.1:" + server.getAddress().getPort());
            assertThat(new OllamaHealthIndicator(client, "qwen-test:7b").health().getStatus()).isEqualTo(Status.UP);
            Health missing = new OllamaHealthIndicator(client, "autre:8b").health();
            assertThat(missing.getStatus()).isEqualTo(Status.UNKNOWN);
            assertThat(missing.getDetails().get("reason").toString()).contains("ollama pull autre:8b");
        } finally {
            server.stop(0);
        }
    }
}
