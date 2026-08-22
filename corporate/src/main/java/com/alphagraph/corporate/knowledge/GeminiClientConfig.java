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
 * <p>{@code HttpRetryOptions} raises the SDK's own retry budget the same way
 * {@code AnthropicClientConfig.MAX_RETRIES} does for the Anthropic client - a transient Gemini
 * failure (429/5xx) should exhaust its own retry budget before {@link NewsExtractor}'s
 * Claude-fallback logic ever triggers, not fall back on the very first blip.
 */
@Configuration
class GeminiClientConfig {

    private static final int MAX_RETRY_ATTEMPTS = 3;

    @Bean
    Client geminiClient() {
        HttpRetryOptions retryOptions = HttpRetryOptions.builder()
            .attempts(MAX_RETRY_ATTEMPTS)
            .httpStatusCodes(429, 500, 502, 503, 504)
            .build();
        return Client.builder()
            .httpOptions(HttpOptions.builder().retryOptions(retryOptions).build())
            .build();
    }
}
