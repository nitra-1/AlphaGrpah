package com.alphagraph.corporate.knowledge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Produces the single {@link AnthropicClient} bean every real Claude caller in this codebase
 * shares - not just {@link DocumentIntelligenceEngine}, but every Stage 2 {@link DocumentExtractor}
 * that calls Claude ({@code ManagementExtractor}, {@code NewsExtractor}, {@code OrderExtractor},
 * {@code FinancialResultsExtractor}), plus {@code decision.analyst.AiAnalystClient} and
 * {@code decision.report.DailyReportClient} (both of which declare their own compile-time
 * dependency on the Anthropic SDK, per {@code decision/build.gradle.kts}'s own comment, but the
 * bean itself is produced only here). {@code fromEnv()} resolves credentials from
 * {@code ANTHROPIC_API_KEY} at application startup - never hardcoded, never routed through
 * Spring's own property resolution. Separated into its own {@code @Bean} (rather than constructed
 * inline in the engine's constructor) so the engine itself can be unit-tested with a mock client,
 * without needing a real API key present - confirmed empirically that {@code fromEnv()} does not
 * throw when the env var is unset (credential resolution is deferred to request time), so this is
 * purely a testability improvement, not a startup-safety requirement.
 *
 * <p>{@code maxRetries(5)} raises the SDK's own default of 2 - every retry is already handled
 * internally by the SDK with exponential backoff on exactly the transient failures worth retrying
 * (408/409/429/5xx and connection errors; a 400/404 - a bad model name, a malformed request - is
 * never retried, since retrying an identical bad request just fails identically). Real motivation:
 * during {@code knowledge-extraction}'s Stage 1 -> Stage 2 pipeline, a document whose Stage 1
 * classification succeeds (billed) but whose Stage 2 extraction then hits a transient `529`
 * aborts the whole document - it stays in `PROCESSED` status and gets reprocessed from scratch on
 * the next sweep, re-billing Stage 1's already-successful call for no reason. Raising the client's
 * own retry budget lets more of those transient failures resolve within the original call, before
 * ever reaching that fall-back-to-a-whole-new-sweep path. Chosen over hand-rolling retry logic in
 * each of the five call sites individually - the SDK's built-in mechanism is already correctly
 * scoped to retryable-vs-not, and one shared client configuration change reaches every caller.
 */
@Configuration
class AnthropicClientConfig {

    private static final int MAX_RETRIES = 5;

    @Bean
    AnthropicClient anthropicClient() {
        return AnthropicOkHttpClient.builder().fromEnv().maxRetries(MAX_RETRIES).build();
    }
}
