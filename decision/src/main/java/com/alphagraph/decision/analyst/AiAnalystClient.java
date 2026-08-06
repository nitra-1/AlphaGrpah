package com.alphagraph.decision.analyst;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Narrates a pre-verified {@link EvidenceFact} list into plain-language bullets - the only Claude
 * call in this package, and the only one anywhere in this project whose output is free-form prose
 * rather than structured JSON, since the whole point here is fluent explanation, not extraction.
 * The prompt explicitly forbids introducing any number or claim not already present in the facts
 * it's given - "AI explains, it never calculates" is enforced by never handing the model anything
 * it could calculate FROM (no raw historical series, no ledger, just the already-computed facts
 * {@link AnalystEvidenceBuilder} produced).
 */
@Component
class AiAnalystClient {

    private static final Model MODEL = Model.CLAUDE_SONNET_5;
    private static final long MAX_TOKENS = 1024L;

    private final AnthropicClient client;

    AiAnalystClient(AnthropicClient client) {
        this.client = client;
    }

    String explain(String symbol, List<EvidenceFact> facts) {
        if (facts.isEmpty()) {
            return "No corporate signal history is available yet for " + symbol + ".";
        }

        MessageCreateParams createParams = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .addUserMessage(buildPrompt(symbol, facts))
            .build();

        return callClaude(createParams);
    }

    private String callClaude(MessageCreateParams createParams) {
        try {
            StringBuilder text = new StringBuilder();
            client.messages().create(createParams).content().stream()
                .flatMap(contentBlock -> contentBlock.text().stream())
                .forEach(textBlock -> text.append(textBlock.text()));
            return text.toString().trim();
        } catch (NotFoundException e) {
            throw new IllegalStateException("Claude API rejected the model/endpoint: " + e.getMessage(), e);
        } catch (RateLimitException e) {
            throw new IllegalStateException("Claude API rate limit hit during analyst narration: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    static String buildPrompt(String symbol, List<EvidenceFact> facts) {
        StringBuilder factList = new StringBuilder();
        for (EvidenceFact fact : facts) {
            factList.append("- ").append(fact.description()).append('\n');
        }

        return """
            You are a financial analyst assistant explaining why %s's outlook has changed, using
            ONLY the verified facts listed below. Every number and claim in these facts has already
            been computed and checked by deterministic code - you must NOT calculate, infer,
            estimate, or introduce any number, percentage, or claim that is not explicitly present
            in the facts below. Do not perform arithmetic on the numbers given.

            Verified facts about %s:
            %s
            Write a short, natural-language bulleted explanation (3-6 bullets) of why %s's outlook
            has changed, drawing only from the facts above. If there are more than 6 facts,
            prioritize the most significant/notable ones. If the facts don't clearly support a
            "why it changed" narrative (for example, only one day of history exists), say so
            plainly and describe the current state instead of inventing a change narrative.

            Output only the bulleted list - no preamble, no closing remarks.
            """.formatted(symbol, symbol, factList, symbol);
    }
}
