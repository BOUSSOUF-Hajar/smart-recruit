package com.smartrecruit.shared.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.time.ZoneId;

import static org.assertj.core.api.Assertions.assertThat;

class AppPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class);

    @Test
    void bindsValidProperties() {
        runner.withPropertyValues(
                        "smartrecruit.app.public-url=https://smartrecruit.example",
                        "smartrecruit.app.default-timezone=Africa/Casablanca")
                .run(context -> {
                    assertThat(context).hasNotFailed();
                    AppProperties properties = context.getBean(AppProperties.class);
                    assertThat(properties.defaultTimezone()).isEqualTo(ZoneId.of("Africa/Casablanca"));
                });
    }

    @Test
    void refusesToStartWhenPublicUrlIsMissing() {
        runner.withPropertyValues("smartrecruit.app.default-timezone=UTC")
                .run(context -> assertThat(context)
                        .hasFailed()
                        .getFailure()
                        .hasStackTraceContaining("publicUrl"));
    }

    @Test
    void refusesAnInvalidPublicUrl() {
        runner.withPropertyValues(
                        "smartrecruit.app.public-url=localhost:4200",
                        "smartrecruit.app.default-timezone=UTC")
                .run(context -> assertThat(context).hasFailed());
    }

    @EnableConfigurationProperties(AppProperties.class)
    static class TestConfig {
    }
}
