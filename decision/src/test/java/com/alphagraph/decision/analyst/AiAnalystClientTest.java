package com.alphagraph.decision.analyst;

import com.anthropic.client.AnthropicClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class AiAnalystClientTest {

    private final AnthropicClient anthropicClient = mock(AnthropicClient.class);
    private final AiAnalystClient client = new AiAnalystClient(anthropicClient);

    @Test
    void emptyFactsListShortCircuitsWithoutCallingClaude() {
        String result = client.explain("BEL", List.of());

        assertThat(result).contains("No corporate signal history").contains("BEL");
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void promptForbidsCalculationAndOnlyReferencesGivenFacts() {
        List<EvidenceFact> facts = List.of(
            new EvidenceFact("ORDER_WIN", "A Rs 2800 Cr order from Ministry of Defence (TENDER_WIN)"),
            new EvidenceFact("SECTOR_STANDING", "Defence sector remains the strongest sector (Sector Score: 90.0, momentum: STRONG)")
        );

        String prompt = AiAnalystClient.buildPrompt("BEL", facts);

        assertThat(prompt)
            .contains("BEL")
            .contains("must NOT calculate, infer,")
            .contains("estimate, or introduce any number")
            .contains("A Rs 2800 Cr order from Ministry of Defence (TENDER_WIN)")
            .contains("Defence sector remains the strongest sector (Sector Score: 90.0, momentum: STRONG)");
    }

    @Test
    void promptInstructsGracefulDegradationWhenNoChangeNarrativeExists() {
        String prompt = AiAnalystClient.buildPrompt("BEL", List.of(new EvidenceFact("SCORE_CURRENT", "Corporate Score is currently 60.0 (NEUTRAL)")));

        assertThat(prompt).contains("only one day of history exists").contains("describe the current state");
    }
}
