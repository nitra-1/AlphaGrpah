package com.alphagraph.corporate.events;

import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.RevenueImpact;
import com.alphagraph.corporate.api.Sentiment;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests the retrofitted rule-based classification logic: topic-to-EventType matching, and
 * deriving category/expectedDuration/revenueImpact/signal from canonical facts and sentiment.
 * Unlike the pre-retrofit engine, there is no Claude call to mock here at all - the whole engine
 * is deterministic, so every test constructs a {@link KnowledgeContext} directly.
 */
class CorporateEventEngineTest {

    private final CorporateEventEngine engine = new CorporateEventEngine(new ObjectMapper());

    @Test
    void matchesMultipleTopicsToMultipleEventTypes() {
        KnowledgeContext context = new KnowledgeContext(
            List.of("Large Order", "Government Contract", "Defence"),
            Map.of(
                "ordervalue", new FactValue("2800", "CRORE"),
                "executionstart", new FactValue("2026", ""),
                "executionend", new FactValue("2029", "")
            ),
            "ORDER_ANNOUNCEMENT", "BEL received a Rs 2,800 Cr order from the Ministry of Defence.",
            Sentiment.POSITIVE, 92.0
        );

        List<ExtractedEvent> events = engine.classify(context);

        assertThat(events).hasSize(2);
        assertThat(events).extracting(ExtractedEvent::eventType)
            .containsExactlyInAnyOrder(EventType.LARGE_ORDER, EventType.GOVERNMENT_CONTRACT);

        ExtractedEvent largeOrder = events.stream().filter(e -> e.eventType() == EventType.LARGE_ORDER).findFirst().orElseThrow();
        assertThat(largeOrder.category()).isEqualTo("Revenue Positive");
        assertThat(largeOrder.summary()).isEqualTo(context.summary());
        assertThat(largeOrder.confidence()).isEqualTo(92.0);
        assertThat(largeOrder.expectedDuration()).isEqualTo("3 Years");
        assertThat(largeOrder.revenueImpact()).isEqualTo(RevenueImpact.HIGH);
        assertThat(largeOrder.signal()).isEqualTo(EventSignal.POSITIVE);
        assertThat(largeOrder.provenance()).contains("LARGE_ORDER", "Large Order");
    }

    @Test
    void noMatchingTopicsReturnsEmptyList() {
        KnowledgeContext context = new KnowledgeContext(
            List.of("Board Meeting", "Routine Filing"), Map.of(),
            "GENERAL_UPDATE", "The board met to discuss routine matters.", Sentiment.NEUTRAL, 80.0
        );

        assertThat(engine.classify(context)).isEmpty();
    }

    @Test
    void topicMatchingIsCaseInsensitiveAndTrims() {
        KnowledgeContext context = new KnowledgeContext(
            List.of("  large order  ", "PATENT"), Map.of(),
            "ORDER_ANNOUNCEMENT", "x", Sentiment.NEUTRAL, 70.0
        );

        List<ExtractedEvent> events = engine.classify(context);

        assertThat(events).extracting(ExtractedEvent::eventType)
            .containsExactlyInAnyOrder(EventType.LARGE_ORDER, EventType.PATENT);
    }

    @Test
    void revenueImpactThresholdsFromOrderValueInCrore() {
        assertThat(revenueImpactFor("1500", "CRORE")).isEqualTo(RevenueImpact.HIGH);
        assertThat(revenueImpactFor("500", "CRORE")).isEqualTo(RevenueImpact.MEDIUM);
        assertThat(revenueImpactFor("50", "CRORE")).isEqualTo(RevenueImpact.LOW);
    }

    @Test
    void revenueImpactConvertsLakhToCrore() {
        // 150000 Lakh = 1500 Cr -> HIGH
        assertThat(revenueImpactFor("150000", "LAKH")).isEqualTo(RevenueImpact.HIGH);
    }

    @Test
    void revenueImpactIsNoneWithoutAnOrderValueFact() {
        KnowledgeContext context = new KnowledgeContext(
            List.of("Merger"), Map.of(), "CORPORATE_ACTION", "x", Sentiment.NEUTRAL, 70.0
        );

        ExtractedEvent event = engine.classify(context).get(0);

        assertThat(event.revenueImpact()).isEqualTo(RevenueImpact.NONE);
    }

    @Test
    void expectedDurationIsNotSpecifiedWithoutExecutionDates() {
        KnowledgeContext context = new KnowledgeContext(
            List.of("Acquisition"), Map.of(), "CORPORATE_ACTION", "x", Sentiment.POSITIVE, 70.0
        );

        ExtractedEvent event = engine.classify(context).get(0);

        assertThat(event.expectedDuration()).isEqualTo("Not specified");
    }

    @Test
    void signalMirrorsDocumentSentiment() {
        KnowledgeContext negative = new KnowledgeContext(
            List.of("Promoter Selling"), Map.of(), "CORPORATE_ACTION", "x", Sentiment.NEGATIVE, 70.0
        );

        assertThat(engine.classify(negative).get(0).signal()).isEqualTo(EventSignal.NEGATIVE);
    }

    private RevenueImpact revenueImpactFor(String value, String unit) {
        KnowledgeContext context = new KnowledgeContext(
            List.of("Large Order"), Map.of("ordervalue", new FactValue(value, unit)),
            "ORDER_ANNOUNCEMENT", "x", Sentiment.POSITIVE, 90.0
        );
        return engine.classify(context).get(0).revenueImpact();
    }
}
