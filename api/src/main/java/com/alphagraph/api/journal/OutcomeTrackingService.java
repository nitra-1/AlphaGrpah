package com.alphagraph.api.journal;

import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates outcomes across every closed (SELL) trade the Trade Journal (Module 3.8) has
 * recorded - compute-on-demand, no new table or scheduler, the same choice Module 3.4's Portfolio
 * Risk made for the same reason: this is cheap arithmetic over already-persisted data, not
 * something that benefits from a daily snapshot. Deliberately does not correlate outcomes back to
 * the Swing Rating active at trade time - the journal doesn't capture it, and reconstructing it
 * after the fact gets tangled with partial buys against a weighted-average cost basis; a real
 * extension, not built here to keep this module's arithmetic unambiguous.
 */
@Service
public class OutcomeTrackingService {

    private final TradeJournalViewService viewService;

    public OutcomeTrackingService(TradeJournalViewService viewService) {
        this.viewService = viewService;
    }

    public OutcomeSummaryDto compute() {
        List<TradeJournalEntryDto> sells = viewService.list().stream()
            .filter(e -> "SELL".equals(e.action()))
            .toList();

        if (sells.isEmpty()) {
            return new OutcomeSummaryDto(null, 0, 0, 0, 0, null, null, null, null, null, List.of());
        }

        BigDecimal totalRealizedPnl = sells.stream().map(TradeJournalEntryDto::realizedPnl).reduce(BigDecimal.ZERO, BigDecimal::add);

        List<TradeJournalEntryDto> wins = sells.stream().filter(e -> e.realizedPnl().signum() > 0).toList();
        List<TradeJournalEntryDto> losses = sells.stream().filter(e -> e.realizedPnl().signum() < 0).toList();
        int breakEvenCount = sells.size() - wins.size() - losses.size();

        double winRatePercent = round2dp((double) wins.size() / sells.size() * 100.0);
        BigDecimal averageWin = average(wins);
        BigDecimal averageLoss = average(losses);

        TradeJournalEntryDto bestTrade = sells.stream().max(Comparator.comparing(TradeJournalEntryDto::realizedPnl)).orElseThrow();
        TradeJournalEntryDto worstTrade = sells.stream().min(Comparator.comparing(TradeJournalEntryDto::realizedPnl)).orElseThrow();

        return new OutcomeSummaryDto(
            totalRealizedPnl, sells.size(), wins.size(), losses.size(), breakEvenCount, winRatePercent,
            averageWin, averageLoss, bestTrade, worstTrade, groupByInstrument(sells)
        );
    }

    private static BigDecimal average(List<TradeJournalEntryDto> entries) {
        if (entries.isEmpty()) {
            return null;
        }
        BigDecimal sum = entries.stream().map(TradeJournalEntryDto::realizedPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
        return sum.divide(BigDecimal.valueOf(entries.size()), 4, RoundingMode.HALF_UP);
    }

    private static List<InstrumentOutcomeDto> groupByInstrument(List<TradeJournalEntryDto> sells) {
        Map<UUID, List<TradeJournalEntryDto>> byInstrumentId = new LinkedHashMap<>();
        for (TradeJournalEntryDto entry : sells) {
            byInstrumentId.computeIfAbsent(entry.instrumentId(), id -> new ArrayList<>()).add(entry);
        }

        List<InstrumentOutcomeDto> result = new ArrayList<>();
        for (Map.Entry<UUID, List<TradeJournalEntryDto>> group : byInstrumentId.entrySet()) {
            List<TradeJournalEntryDto> trades = group.getValue();
            BigDecimal pnl = trades.stream().map(TradeJournalEntryDto::realizedPnl).reduce(BigDecimal.ZERO, BigDecimal::add);
            int wins = (int) trades.stream().filter(e -> e.realizedPnl().signum() > 0).count();
            int losses = (int) trades.stream().filter(e -> e.realizedPnl().signum() < 0).count();
            double winRate = round2dp((double) wins / trades.size() * 100.0);
            result.add(new InstrumentOutcomeDto(group.getKey(), trades.get(0).symbol(), pnl, trades.size(), wins, losses, winRate));
        }
        result.sort(Comparator.comparing(InstrumentOutcomeDto::realizedPnl).reversed());
        return result;
    }

    private static double round2dp(double value) {
        return Math.round(value * 100.0) / 100.0;
    }
}
