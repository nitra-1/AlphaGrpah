package com.alphagraph.corporate.knowledge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Produces the single {@link AnthropicClient} bean {@link DocumentIntelligenceEngine} depends on -
 * the only Claude caller left in the corporate module now that Corporate Event Engine and Order
 * Book Engine are deterministic rule engines reading canonical facts/topics instead of calling
 * Claude directly. {@code fromEnv()} resolves credentials from {@code ANTHROPIC_API_KEY} at
 * application startup - never hardcoded, never routed through Spring's own property resolution.
 * Separated into its own {@code @Bean} (rather than constructed inline in the engine's
 * constructor) so the engine itself can be unit-tested with a mock client, without needing a real
 * API key present - confirmed empirically that {@code fromEnv()} does not throw when the env var
 * is unset (credential resolution is deferred to request time), so this is purely a testability
 * improvement, not a startup-safety requirement.
 */
@Configuration
class AnthropicClientConfig {

    @Bean
    AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.fromEnv();
    }
}
