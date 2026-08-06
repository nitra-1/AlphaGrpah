package com.alphagraph.decision.report;

import com.anthropic.client.AnthropicClient;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.NotFoundException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.Model;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;

/**
 * Narrates one day's pre-verified {@link ReportFact} list into a short summary paragraph - the
 * same discipline intelligence.analyst.AiAnalystClient (Module 2.9) established: the prompt
 * forbids introducing any number or claim not already present in the facts, since every number
 * was already computed and checked by {@link DailyReportEvidenceBuilder} before this class ever
 * runs. Not a reuse of AiAnalystClient itself - that one explains a single instrument's Corporate
 * Score history, this one synthesizes a whole day's cross-instrument digest, a materially
 * different prompt shape.
 */
@Component
class DailyReportClient {

    private static final Model MODEL = Model.CLAUDE_SONNET_5;
    private static final long MAX_TOKENS = 1024L;

    private final AnthropicClient client;

    DailyReportClient(AnthropicClient client) {
        this.client = client;
    }

    String narrate(LocalDate reportDate, List<ReportFact> facts) {
        if (facts.isEmpty()) {
            return "No notable rank movements, corporate events, guidance changes, or news were recorded for " + reportDate + ".";
        }

        MessageCreateParams createParams = MessageCreateParams.builder()
            .model(MODEL)
            .maxTokens(MAX_TOKENS)
            .addUserMessage(buildPrompt(reportDate, facts))
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
            throw new IllegalStateException("Claude API rate limit hit during daily report narration: " + e.getMessage(), e);
        } catch (AnthropicIoException e) {
            throw new IllegalStateException("Network failure calling Claude API: " + e.getMessage(), e);
        } catch (AnthropicServiceException e) {
            throw new IllegalStateException("Claude API call failed: " + e.getMessage(), e);
        }
    }

    static String buildPrompt(LocalDate reportDate, List<ReportFact> facts) {
        StringBuilder factList = new StringBuilder();
        for (ReportFact fact : facts) {
            factList.append("- [").append(fact.factType()).append("] ").append(fact.description()).append('\n');
        }

        return """
            You are a financial analyst assistant writing a daily market digest for %s, using ONLY
            the verified facts listed below. Every number and claim in these facts has already
            been computed and checked by deterministic code - you must NOT calculate, infer,
            estimate, or introduce any number, percentage, or claim that is not explicitly present
            in the facts below. Do not perform arithmetic on the numbers given.

            Verified facts for %s:
            %s
            Write a short daily report (one summary paragraph, followed by 3-6 bullets covering the
            most significant facts - prioritize RANK_IMPROVEMENT/RANK_DECLINE and
            WATCHLIST_MOVER/PORTFOLIO_MOVER facts first, since those are what a reader is most
            likely tracking). If there are more facts than fit in 6 bullets, prioritize the most
            significant ones and omit the rest rather than cramming everything in.

            Output only the summary paragraph and bulleted list - no preamble, no closing remarks.
            """.formatted(reportDate, reportDate, factList);
    }
}
