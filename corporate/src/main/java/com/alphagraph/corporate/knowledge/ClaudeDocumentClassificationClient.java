package com.alphagraph.corporate.knowledge;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import org.springframework.stereotype.Component;

/** {@link DocumentIntelligenceEngine}'s original, always-available Claude call - stays the only path for every non-NEWS document, and the fallback for NEWS documents once the Gemini pilot is primary. */
@Component
class ClaudeDocumentClassificationClient implements DocumentClassificationExtractionClient {

    private final AnthropicClient client;

    ClaudeDocumentClassificationClient(AnthropicClient client) {
        this.client = client;
    }

    @Override
    public String extractRawJson(String documentText) {
        MessageCreateParams createParams = MessageCreateParams.builder()
            .model(DocumentIntelligenceEngine.MODEL)
            .maxTokens(DocumentIntelligenceEngine.MAX_TOKENS)
            .outputConfig(DocumentIntelligenceEngine.buildOutputConfig())
            .addUserMessage(DocumentIntelligenceEngine.buildPrompt(documentText))
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
            throw new IllegalStateException("Claude API rate limit hit during document classification: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }
}
