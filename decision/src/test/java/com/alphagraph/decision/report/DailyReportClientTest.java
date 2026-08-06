package com.alphagraph.decision.report;

import com.anthropic.client.AnthropicClient;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class DailyReportClientTest {

    private final AnthropicClient anthropicClient = mock(AnthropicClient.class);
    private final DailyReportClient client = new DailyReportClient(anthropicClient);
    private final LocalDate reportDate = LocalDate.of(2026, 6, 1);

    @Test
    void emptyFactsListShortCircuitsWithoutCallingClaude() {
        String result = client.narrate(reportDate, List.of());

        assertThat(result).contains("No notable").contains(reportDate.toString());
        verifyNoInteractions(anthropicClient);
    }

    @Test
    void promptForbidsCalculationAndOnlyReferencesGivenFacts() {
        List<ReportFact> facts = List.of(
            new ReportFact("RANK_IMPROVEMENT", "TCS moved from Rank 4 to Rank 1 (improved by 3)"),
            new ReportFact("WATCHLIST_MOVER", "Watchlist: INFY Swing Rank improved by 2 (now Rank 2)")
        );

        String prompt = DailyReportClient.buildPrompt(reportDate, facts);

        assertThat(prompt)
            .contains(reportDate.toString())
            .contains("must NOT calculate, infer,")
            .contains("estimate, or introduce any number")
            .contains("TCS moved from Rank 4 to Rank 1 (improved by 3)")
            .contains("Watchlist: INFY Swing Rank improved by 2 (now Rank 2)");
    }

    @Test
    void promptPrioritizesRankAndWatchlistPortfolioFactsFirst() {
        String prompt = DailyReportClient.buildPrompt(reportDate, List.of(new ReportFact("RANK_IMPROVEMENT", "x")));

        assertThat(prompt).contains("RANK_IMPROVEMENT/RANK_DECLINE").contains("WATCHLIST_MOVER/PORTFOLIO_MOVER");
    }
}
