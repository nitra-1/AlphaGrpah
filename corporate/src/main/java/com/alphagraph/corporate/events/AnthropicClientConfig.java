package com.alphagraph.corporate.events;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Produces the single {@link AnthropicClient} bean {@link CorporateEventEngine} depends on.
 * {@code fromEnv()} resolves credentials from {@code ANTHROPIC_API_KEY} at application startup -
 * never hardcoded, never routed through Spring's own property resolution. Separated into its own
 * {@code @Bean} (rather than constructed inline in the engine's constructor) so the engine itself
 * can be unit-tested with a mock client, without needing a real API key present.
 */
@Configuration
class AnthropicClientConfig {

    @Bean
    AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.fromEnv();
    }
}
