package com.alphagraph.corporate.knowledge;

import com.google.genai.Client;
import com.google.genai.types.HttpOptions;
import com.google.genai.types.HttpRetryOptions;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Produces the single Gemini {@link Client} bean {@link GeminiNewsExtractionClient} depends on -
 * the Gemini free-tier pilot's only real Claude alternative in this codebase (Module: NewsExtractor
 * Gemini pilot). Mirrors {@link AnthropicClientConfig} exactly: {@code new Client()} resolves
 * credentials from {@code GOOGLE_API_KEY} at call time - never hardcoded, never routed through
 * Spring's own property resolution.
 *
 * <p>The SDK's own retry is deliberately left at a single attempt: retrying, pacing to the
 * provider's per-minute cap, honouring a 429's requested wait and the circuit breaker all live in
 * {@link GeminiCallExecutor}, which every Gemini call goes through. Leaving the SDK's built-in
 * retry on would stack hidden retries (each one spending quota) under the executor's visible ones.
 */
@Configuration
class GeminiClientConfig {

    private static final int SDK_ATTEMPTS = 1; // 1 = no SDK-level retry; GeminiCallExecutor owns retries

    @Bean
    Client geminiClient() {
        HttpRetryOptions retryOptions = HttpRetryOptions.builder()
            .attempts(SDK_ATTEMPTS)
            .build();
        return Client.builder()
            .httpOptions(HttpOptions.builder().retryOptions(retryOptions).build())
            .build();
    }
}
