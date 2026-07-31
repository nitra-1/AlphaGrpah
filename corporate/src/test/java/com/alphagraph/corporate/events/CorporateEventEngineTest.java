package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.RevenueImpact;
import com.anthropic.client.AnthropicClient;
import com.anthropic.models.messages.OutputConfig;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.mockito.Mockito.mock;

/**
 * Tests the parsing/validation logic that turns Claude's structured-output JSON into
 * {@link ExtractedEvent}s, plus prompt/schema construction. Does not exercise
 * {@link CorporateEventEngine#extractEvents(String)}'s live Claude API call - that is covered by
 * this module's live end-to-end verification (see claude.md), matching every other module's split
 * between unit-testable pure logic and a separate real-service verification step.
 */
class CorporateEventEngineTest {

    private final CorporateEventEngine engine = new CorporateEventEngine(mock(AnthropicClient.class), new ObjectMapper());

    @Test
    void parsesMultipleEventsFromValidJson() {
        String json = """
            {
              "events": [
                {
                  "eventType": "LARGE_ORDER",
                  "category": "Revenue Positive",
                  "summary": "BEL received a Rs 2,800 Cr defence order.",
                  "confidence": 98,
                  "expectedDuration": "3 Years",
                  "revenueImpact": "HIGH",
                  "signal": "POSITIVE"
                },
                {
                  "eventType": "DEBT_RAISING",
                  "category": "Financing",
                  "summary": "The company raised Rs 500 Cr via NCDs.",
                  "confidence": 80,
                  "expectedDuration": "One-time",
                  "revenueImpact": "NONE",
                  "signal": "NEUTRAL"
                }
              ]
            }
            """;

        List<ExtractedEvent> events = engine.parseEvents(json);

        assertThat(events).hasSize(2);
        ExtractedEvent first = events.get(0);
        assertThat(first.eventType()).isEqualTo(EventType.LARGE_ORDER);
        assertThat(first.category()).isEqualTo("Revenue Positive");
        assertThat(first.summary()).isEqualTo("BEL received a Rs 2,800 Cr defence order.");
        assertThat(first.confidence()).isEqualTo(98);
        assertThat(first.expectedDuration()).isEqualTo("3 Years");
        assertThat(first.revenueImpact()).isEqualTo(RevenueImpact.HIGH);
        assertThat(first.signal()).isEqualTo(EventSignal.POSITIVE);
        assertThat(first.rawResponse()).isEqualTo(json);

        assertThat(events.get(1).eventType()).isEqualTo(EventType.DEBT_RAISING);
        assertThat(events.get(1).revenueImpact()).isEqualTo(RevenueImpact.NONE);
    }

    @Test
    void emptyEventsArrayIsAValidOutcomeNotAnError() {
        String json = "{\"events\": []}";

        List<ExtractedEvent> events = engine.parseEvents(json);

        assertThat(events).isEmpty();
    }

    @Test
    void malformedJsonThrowsIllegalStateException() {
        assertThatIllegalStateException()
            .isThrownBy(() -> engine.parseEvents("not valid json"))
            .withMessageContaining("Could not parse");
    }

    @Test
    void valueOutsideDeclaredEnumThrowsIllegalStateException() {
        String json = """
            {"events": [{
              "eventType": "SOMETHING_NOT_IN_THE_SCHEMA",
              "category": "Revenue Positive", "summary": "x", "confidence": 90,
              "expectedDuration": "1 Year", "revenueImpact": "HIGH", "signal": "POSITIVE"
            }]}
            """;

        assertThatIllegalStateException()
            .isThrownBy(() -> engine.parseEvents(json))
            .withMessageContaining("outside the declared schema enum");
    }

    @Test
    void outOfRangeConfidenceThrowsIllegalStateException() {
        LlmEvent event = new LlmEvent("LARGE_ORDER", "Revenue Positive", "x", 150, "1 Year", "HIGH", "POSITIVE");

        assertThatIllegalStateException()
            .isThrownBy(() -> engine.toExtractedEvent(event, "{}"))
            .withMessageContaining("out-of-range confidence");
    }

    @Test
    void promptIncludesAllThirteenCategoriesAndTheDocumentText() {
        String prompt = CorporateEventEngine.buildPrompt("BEL received a Rs 2,800 Cr order from the Ministry of Defence.");

        assertThat(prompt)
            .contains("Large Order", "Capacity Expansion", "New Plant", "Acquisition", "Merger", "Joint Venture")
            .contains("PLI Approval", "Patent", "Export Approval", "Government Contract", "Debt Raising")
            .contains("Promoter Buying", "Promoter Selling")
            .contains("BEL received a Rs 2,800 Cr order from the Ministry of Defence.");
    }

    @Test
    void outputConfigBuildsWithoutThrowing() {
        OutputConfig outputConfig = CorporateEventEngine.buildOutputConfig();

        assertThat(outputConfig).isNotNull();
    }
}
