package com.alphagraph.corporate.knowledge;

import com.alphagraph.corporate.api.Sentiment;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Tests routing/dispatch logic in isolation from any real extractor implementation. */
class DocumentRouterTest {

    @Test
    void dispatchesOnlyToExtractorsThatSupportTheClassification() {
        FakeExtractor orderLike = new FakeExtractor(true, List.of(new ExtractedFact("orderlifecyclestage", "NEW_ORDER", "", 90)));
        FakeExtractor unrelated = new FakeExtractor(false, List.of(new ExtractedFact("shouldnotappear", "x", "", 90)));
        DocumentRouter router = new DocumentRouter(List.of(orderLike, unrelated));

        List<ExtractedFact> facts = router.route(context());

        assertThat(facts).extracting(ExtractedFact::factType).containsExactly("orderlifecyclestage");
        assertThat(orderLike.extractCalled).isTrue();
        assertThat(unrelated.extractCalled).isFalse();
    }

    @Test
    void aggregatesFactsFromMultipleMatchingExtractors() {
        FakeExtractor first = new FakeExtractor(true, List.of(new ExtractedFact("a", "1", "", 90)));
        FakeExtractor second = new FakeExtractor(true, List.of(new ExtractedFact("b", "2", "", 90)));
        DocumentRouter router = new DocumentRouter(List.of(first, second));

        List<ExtractedFact> facts = router.route(context());

        assertThat(facts).extracting(ExtractedFact::factType).containsExactlyInAnyOrder("a", "b");
    }

    @Test
    void noMatchingExtractorsYieldsNoFacts() {
        DocumentRouter router = new DocumentRouter(List.of(new FakeExtractor(false, List.of())));

        assertThat(router.route(context())).isEmpty();
    }

    @Test
    void oneExtractorFailingDoesNotStopOthersFromRunning() {
        DocumentExtractor throwing = new DocumentExtractor() {
            @Override
            public boolean supports(DocumentClassification classification) {
                return true;
            }

            @Override
            public ExtractionResult extract(DocumentContext context) {
                throw new IllegalStateException("simulated extractor failure");
            }
        };
        FakeExtractor healthy = new FakeExtractor(true, List.of(new ExtractedFact("still-works", "x", "", 90)));
        DocumentRouter router = new DocumentRouter(List.of(throwing, healthy));

        List<ExtractedFact> facts = router.route(context());

        assertThat(facts).extracting(ExtractedFact::factType).containsExactly("still-works");
    }

    private DocumentContext context() {
        DocumentClassification classification = new DocumentClassification(
            "ORDER_ANNOUNCEMENT", List.of(), List.of(), "x", Sentiment.NEUTRAL, 80.0, List.of("ORDER")
        );
        return new DocumentContext(UUID.randomUUID(), UUID.randomUUID(), "TEST", "document text", classification);
    }

    private static class FakeExtractor implements DocumentExtractor {
        private final boolean supports;
        private final List<ExtractedFact> facts;
        boolean extractCalled = false;

        FakeExtractor(boolean supports, List<ExtractedFact> facts) {
            this.supports = supports;
            this.facts = facts;
        }

        @Override
        public boolean supports(DocumentClassification classification) {
            return supports;
        }

        @Override
        public ExtractionResult extract(DocumentContext context) {
            extractCalled = true;
            return new ExtractionResult(facts);
        }
    }
}
