package com.smartrecruit.ai.infrastructure;

import com.smartrecruit.ai.api.AiStatus.State;
import com.smartrecruit.ai.application.AIService;
import com.smartrecruit.ai.application.AiProperties;
import com.smartrecruit.ai.infrastructure.ollama.OllamaHealthIndicator;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.web.client.RestClient;

import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/** US-46 : état de l'IA vu par l'admin, sans démarrer Spring. */
class AiStatusAdapterTest {

    private final MovableClock clock = new MovableClock();

    @Test
    void stoppedOllamaIsShownUnavailableWithTheConfiguredModel() {
        OllamaHealthIndicator stopped = new OllamaHealthIndicator(RestClient.create("http://127.0.0.1:1"), "qwen-test");
        AiStatusAdapter adapter = adapter(true, Map.of("health", stopped));

        assertThat(adapter.current().state()).isEqualTo(State.UNAVAILABLE);
        assertThat(adapter.current().model()).isEqualTo("qwen-test");
    }

    @Test
    void disabledAiIsNeverProbed() {
        assertThat(adapter(false, Map.of()).current().state()).isEqualTo(State.DISABLED);
    }

    @Test
    void theProbeResultIsKeptThirtySeconds() {
        AtomicInteger probes = new AtomicInteger();
        OllamaHealthIndicator counting = new OllamaHealthIndicator(RestClient.create("http://127.0.0.1:1"), "m") {
            @Override
            public org.springframework.boot.health.contributor.Health health() {
                probes.incrementAndGet();
                return super.health();
            }
        };
        AiStatusAdapter adapter = adapter(true, Map.of("health", counting));

        adapter.current();
        clock.advance(Duration.ofSeconds(29));
        adapter.current();
        assertThat(probes).hasValue(1);
        clock.advance(Duration.ofSeconds(2));
        adapter.current();
        assertThat(probes).hasValue(2);
    }

    private AiStatusAdapter adapter(boolean enabled, Map<String, Object> beans) {
        AiProperties properties = new AiProperties(enabled, "ollama", 12000, 10,
                new AiProperties.Ollama(URI.create("http://127.0.0.1:1"), "qwen-test", Duration.ofSeconds(1),
                        Duration.ofSeconds(1), false),
                new AiProperties.Executor(1, 1));
        AIService service = new DisabledAIService() {
            @Override
            public String modelName() {
                return "qwen-test";
            }
        };
        return new AiStatusAdapter(properties, service,
                new StaticListableBeanFactory(beans).getBeanProvider(OllamaHealthIndicator.class), clock);
    }

    private static final class MovableClock extends Clock {

        private Instant now = Instant.parse("2026-10-23T10:00:00Z");

        void advance(Duration duration) {
            now = now.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
