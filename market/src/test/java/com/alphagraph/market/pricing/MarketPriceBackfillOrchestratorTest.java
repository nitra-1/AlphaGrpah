package com.alphagraph.market.pricing;

import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MarketPriceBackfillOrchestratorTest {

    // Fixed to a Friday (Asia/Kolkata), matching HistoricalBackfillServiceTest - "yesterday" is
    // a genuine weekday so the walk starts immediately, no leading weekend skip to account for.
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-07-24T12:00:00Z"), ZoneId.of("Asia/Kolkata")
    );
    private static final DateTimeFormatter ROW_DATE_FORMAT = DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private final BackfillCandidateReader candidateReader = mock(BackfillCandidateReader.class);
    private final HistoricalBackfillService backfillService = mock(HistoricalBackfillService.class);
    private final DiscoveredPriceWriter discoveredPriceWriter = mock(DiscoveredPriceWriter.class);
    private final MarketPriceBackfillOrchestrator orchestrator =
        new MarketPriceBackfillOrchestrator(candidateReader, backfillService, discoveredPriceWriter, FIXED_CLOCK);

    /**
     * Real 15-field column order: SYMBOL(0) SERIES(1) DATE1(2) PREV_CLOSE(3) OPEN_PRICE(4)
     * HIGH_PRICE(5) LOW_PRICE(6) LAST_PRICE(7) CLOSE_PRICE(8) AVG_PRICE(9) TTL_TRD_QNTY(10)
     * TURNOVER_LACS(11) NO_OF_TRADES(12) DELIV_QTY(13) DELIV_PER(14) - the orchestrator's
     * {@code toRawDeliveryRow} indexes into this same layout.
     */
    private static String[] rowFor(String symbol, LocalDate date) {
        return new String[] {
            symbol, "EQ", date.format(ROW_DATE_FORMAT), "979.00", "975.20", "1051.00", "971.60",
            "1040.90", "1039.40", "1027.67", "2105588", "21638.42", "80353", "475167", "22.57"
        };
    }

    @Test
    void noCandidatesMeansNoFetchingAtAll() {
        when(candidateReader.findSymbolsNeedingBackfill(MarketPriceBackfillOrchestrator.TARGET_ROWS)).thenReturn(List.of());

        orchestrator.backfillDiscoveryCandidates();

        verifyNoInteractions(backfillService, discoveredPriceWriter);
    }

    @Test
    void stopsFetchingASymbolAsSoonAsItReachesTargetRows() {
        when(candidateReader.findSymbolsNeedingBackfill(MarketPriceBackfillOrchestrator.TARGET_ROWS))
            .thenReturn(List.of("AASTHA"));
        when(candidateReader.findExistingTradeDates(List.of("AASTHA"))).thenReturn(Map.of());
        // Every fetched day returns a genuinely new row for AASTHA (its own real trade date).
        when(backfillService.fetchEquityRows(any(LocalDate.class)))
            .thenAnswer(invocation -> List.<String[]>of(rowFor("AASTHA", invocation.getArgument(0))));

        orchestrator.backfillDiscoveryCandidates();

        verify(backfillService, times(MarketPriceBackfillOrchestrator.TARGET_ROWS)).fetchEquityRows(any(LocalDate.class));
        verify(discoveredPriceWriter, times(MarketPriceBackfillOrchestrator.TARGET_ROWS)).capture(any());
    }

    @Test
    void neverFoundSymbolStopsAtMaxLookbackDaysRatherThanWalkingForever() {
        when(candidateReader.findSymbolsNeedingBackfill(MarketPriceBackfillOrchestrator.TARGET_ROWS))
            .thenReturn(List.of("JUSTIPOED"));
        when(candidateReader.findExistingTradeDates(List.of("JUSTIPOED"))).thenReturn(Map.of());
        when(backfillService.fetchEquityRows(any(LocalDate.class))).thenReturn(List.of());

        orchestrator.backfillDiscoveryCandidates();

        verify(backfillService, times(MarketPriceBackfillOrchestrator.MAX_LOOKBACK_DAYS)).fetchEquityRows(any(LocalDate.class));
        verifyNoInteractions(discoveredPriceWriter);
    }

    @Test
    void preExistingRowsReduceHowManyMoreAreFetched() {
        Set<LocalDate> nineteenExistingDates = new HashSet<>();
        LocalDate d = LocalDate.of(2026, 7, 1);
        for (int i = 0; i < 19; i++) {
            nineteenExistingDates.add(d.plusDays(i));
        }
        when(candidateReader.findSymbolsNeedingBackfill(MarketPriceBackfillOrchestrator.TARGET_ROWS))
            .thenReturn(List.of("SUNSHINE"));
        when(candidateReader.findExistingTradeDates(List.of("SUNSHINE")))
            .thenReturn(Map.of("SUNSHINE", nineteenExistingDates));
        // The first (and only) day fetched hands back a brand-new date - exactly one more row closes the gap to 20.
        when(backfillService.fetchEquityRows(any(LocalDate.class)))
            .thenReturn(List.<String[]>of(rowFor("SUNSHINE", LocalDate.of(2026, 6, 1))));

        orchestrator.backfillDiscoveryCandidates();

        verify(backfillService, times(1)).fetchEquityRows(any(LocalDate.class));
        verify(discoveredPriceWriter, times(1)).capture(any());
    }

    @Test
    void aRowForADateAlreadyOnRecordIsNotCapturedOrCountedAsProgress() {
        LocalDate alreadyKnown = LocalDate.of(2026, 7, 23);
        when(candidateReader.findSymbolsNeedingBackfill(MarketPriceBackfillOrchestrator.TARGET_ROWS))
            .thenReturn(List.of("AASTHA"));
        when(candidateReader.findExistingTradeDates(List.of("AASTHA")))
            .thenReturn(Map.of("AASTHA", new HashSet<>(Set.of(alreadyKnown))));
        // NSE's "holiday re-serves the prior file" quirk: every day fetched hands back the SAME
        // already-known date - the ON CONFLICT upsert target, never new progress.
        when(backfillService.fetchEquityRows(any(LocalDate.class)))
            .thenReturn(List.<String[]>of(rowFor("AASTHA", alreadyKnown)));

        orchestrator.backfillDiscoveryCandidates();

        verify(backfillService, times(MarketPriceBackfillOrchestrator.MAX_LOOKBACK_DAYS)).fetchEquityRows(any(LocalDate.class));
        verifyNoInteractions(discoveredPriceWriter);
    }
}
