package com.smartrecruit.ai.infrastructure;

import com.smartrecruit.ai.application.AIService;
import com.smartrecruit.ai.application.AiProperties;
import com.smartrecruit.ai.infrastructure.ollama.OllamaAIService;
import com.smartrecruit.ai.infrastructure.ollama.OllamaHealthIndicator;
import jakarta.validation.Validator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.net.http.HttpClient;

/**
 * Choix de l'implémentation d'{@link AIService} (architecture backend, « Adaptateur Ollama ») :
 * <ul>
 *   <li>{@code smartrecruit.ai.enabled=false} (défaut) → {@link DisabledAIService} ;</li>
 *   <li>{@code enabled=true}, {@code provider=ollama} → {@link OllamaAIService} et son indicateur de santé ;</li>
 *   <li>{@code provider=fake} → la fausse implémentation des tests, déclarée dans les sources de test.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class AiConfig {

    /** Pool borné : sur CPU, Ollama traite mal les analyses simultanées. Arrêt propre en 30 s au plus. */
    @Bean(name = "aiExecutor", destroyMethod = "shutdown")
    ThreadPoolTaskExecutor aiExecutor(AiProperties properties) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(properties.executor().threads());
        executor.setMaxPoolSize(properties.executor().threads());
        executor.setQueueCapacity(properties.executor().queueCapacity());
        executor.setThreadNamePrefix("ai-");
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(30);
        executor.initialize();
        return executor;
    }

    @Bean
    @ConditionalOnExpression("${smartrecruit.ai.enabled:false} and '${smartrecruit.ai.provider:ollama}' == 'ollama'")
    RestClient ollamaRestClient(AiProperties properties) {
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(properties.ollama().connectTimeout()).build());
        factory.setReadTimeout(properties.ollama().readTimeout());
        return RestClient.builder()
                .baseUrl(properties.ollama().baseUrl().toString())
                .requestFactory(factory)
                .build();
    }

    @Bean
    @ConditionalOnExpression("${smartrecruit.ai.enabled:false} and '${smartrecruit.ai.provider:ollama}' == 'ollama'")
    AIService ollamaAIService(RestClient ollamaRestClient, AiProperties properties, PromptLibrary prompts,
                              JsonMapper json, Validator validator) {
        return new OllamaAIService(ollamaRestClient, properties.ollama().model(), prompts, json, validator);
    }

    @Bean
    @ConditionalOnExpression("${smartrecruit.ai.enabled:false} and '${smartrecruit.ai.provider:ollama}' == 'ollama'")
    OllamaHealthIndicator ollamaHealthIndicator(RestClient ollamaRestClient, AiProperties properties) {
        return new OllamaHealthIndicator(ollamaRestClient, properties.ollama().model());
    }

    /**
     * Désactivé : rien ne s'exécute, AI_DISABLED partout. Activé avec un fournisseur sans
     * implémentation : aucun bean, le démarrage échoue tout de suite (configuration à corriger).
     */
    @Bean
    @ConditionalOnExpression("!${smartrecruit.ai.enabled:false}")
    AIService disabledAIService() {
        return new DisabledAIService();
    }
}
