package com.alphagraph.corporate.knowledge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import org.springframework.stereotype.Component;

/** {@link NewsExtractor}'s original, always-available Claude call - today's fallback path once the Gemini pilot is primary. */
@Component
class ClaudeNewsExtractionClient implements NewsImpactExtractionClient {

    private final AnthropicClient client;

    ClaudeNewsExtractionClient(AnthropicClient client) {
        this.client = client;
    }

    @Override
    public String extractRawJson(String documentText) {
        MessageCreateParams createParams = MessageCreateParams.builder()
            .model(NewsExtractor.MODEL)
            .maxTokens(NewsExtractor.MAX_TOKENS)
            .outputConfig(NewsExtractor.buildOutputConfig())
            .addUserMessage(NewsExtractor.buildPrompt(documentText))
            .build();

        try {
            StringBuilder rawJson = new StringBuilder();
            client.messages().create(createParams).content().stream()
                .flatMap(contentBlock -> contentBlock.text().stream())
                .forEach(textBlock -> rawJson.append(textBlock.text()));
            return rawJson.toString();
        } catch (NotFoundException e) {
            throw new IllegalStateException("Claude API rejected the model/endpoint: " + e.getMessage(), e);
        } catch (RateLimitException e) {
            throw new IllegalStateException("Claude API rate limit hit during news extraction: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }
}
