package com.alphagraph.market.pricing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Requirement-driven backfill for Discovery candidates' real trading history in
 * {@code market.discovered_prices} - without at least {@value #TARGET_ROWS} genuine trading
 * sessions, {@code ownership.deals.MarketLiquidityReader}'s 20-trading-day ADTV is never
 * computable for a symbol, and a fixed calendar-day guess (like the original ~35-day draft of
 * this sprint) is wrong on both sides: too few calendar days for a low-liquidity symbol with
 * frequent gaps, too many (wasted fetches) for a highly liquid one.
 *
 * <p>Walks backward from yesterday one calendar day at a time (skipping weekends, matching
 * {@link HistoricalBackfillService}), fetching each day's full bhavdata file exactly once via
 * {@link HistoricalBackfillService#fetchEquityRows} and extracting every still-needed symbol's
 * row out of it - one fetch per day covers every candidate, not one fetch per symbol per day.
 * Stops each symbol individually once it reaches {@value #TARGET_ROWS} distinct real trading
 * sessions *strictly before that symbol's own {@link BackfillTarget#targetBeforeDate}* (see
 * {@link BackfillCandidateReader} - not just any 20 total, a real bug caught live: a symbol
 * discovered the day after its first deal would otherwise land exactly 20 total rows ending *on*
 * that deal's own date, one short of what's needed strictly before it), and stops the whole walk
 * once either every candidate has reached that or {@value #MAX_LOOKBACK_DAYS} calendar days have
 * been walked - a real, disclosed limit: a symbol that IPO'd fewer than {@value #TARGET_ROWS}
 * trading sessions ago will correctly never reach the target, and {@code MarketLiquidityReader}
 * will correctly report ADTV unavailable for it rather than a guess extrapolated from too little
 * history.
 *
 * <p>Tracks progress by distinct {@code trade_date} per symbol (seeded from what's already in
 * {@code discovered_prices}), not by write-call count - the walk window can revisit a date the
 * normal daily gated capture (see {@link BhavdataNormalizer}) already wrote yesterday, and an
 * {@code ON CONFLICT DO UPDATE} upsert on that date must not be double-counted as new progress.
 */
@Component
class MarketPriceBackfillOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceBackfillOrchestrator.class);
    static final int TARGET_ROWS = 20;
    static final int MAX_LOOKBACK_DAYS = 60;

    private final BackfillCandidateReader candidateReader;
    private final HistoricalBackfillService backfillService;
    private final DiscoveredPriceWriter discoveredPriceWriter;
    private final Clock clock;

    @Autowired
    MarketPriceBackfillOrchestrator(
        BackfillCandidateReader candidateReader, HistoricalBackfillService backfillService,
        DiscoveredPriceWriter discoveredPriceWriter
    ) {
        this(candidateReader, backfillService, discoveredPriceWriter, Clock.system(ZoneId.of("Asia/Kolkata")));
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    MarketPriceBackfillOrchestrator(
        BackfillCandidateReader candidateReader, HistoricalBackfillService backfillService,
        DiscoveredPriceWriter discoveredPriceWriter, Clock clock
    ) {
        this.candidateReader = candidateReader;
        this.backfillService = backfillService;
        this.discoveredPriceWriter = discoveredPriceWriter;
        this.clock = clock;
    }

    void backfillDiscoveryCandidates() {
        LocalDate today = LocalDate.now(clock);
        List<BackfillTarget> candidates = candidateReader.findSymbolsNeedingBackfill(TARGET_ROWS, today);
        if (candidates.isEmpty()) {
            log.info("Discovery price backfill: no symbols need backfilling.");
            return;
        }

        Map<String, LocalDate> targetBeforeDateBySymbol = new HashMap<>();
        for (BackfillTarget target : candidates) {
            targetBeforeDateBySymbol.put(target.symbol(), target.targetBeforeDate());
        }
        List<String> symbolNames = candidates.stream().map(BackfillTarget::symbol).toList();

        Map<String, Set<LocalDate>> tradeDatesBySymbol = new HashMap<>(candidateReader.findExistingTradeDates(symbolNames));
        Set<String> stillNeeded = new HashSet<>();
        for (BackfillTarget target : candidates) {
            Set<LocalDate> existing = tradeDatesBySymbol.computeIfAbsent(target.symbol(), key -> new HashSet<>());
            if (qualifyingCount(existing, target.targetBeforeDate()) < TARGET_ROWS) {
                stillNeeded.add(target.symbol());
            }
        }

        LocalDate date = today.minusDays(1);
        int daysWalked = 0;
        int rowsWritten = 0;
        while (!stillNeeded.isEmpty() && daysWalked < MAX_LOOKBACK_DAYS) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                for (String[] fields : backfillService.fetchEquityRows(date)) {
                    String symbol = fields[0].trim();
                    if (!stillNeeded.contains(symbol)) {
                        continue;
                    }
                    LocalDate tradeDate;
                    try {
                        tradeDate = LocalDate.parse(fields[2].trim(), BhavdataNormalizer.DATE_FORMAT);
                    } catch (Exception e) {
                        continue;
                    }
                    Set<LocalDate> dates = tradeDatesBySymbol.get(symbol);
                    if (dates.add(tradeDate)) {
                        // Still captured even if it falls on/after the target date - harmless
                        // (idempotent upsert) and useful for other purposes - it just doesn't
                        // count toward *this* symbol's "sessions before its own target" requirement.
                        discoveredPriceWriter.capture(toRawDeliveryRow(fields));
                        rowsWritten++;
                    }
                    if (qualifyingCount(dates, targetBeforeDateBySymbol.get(symbol)) >= TARGET_ROWS) {
                        stillNeeded.remove(symbol);
                    }
                }
                daysWalked++;
            }
            date = date.minusDays(1);
        }

        log.info(
            "Discovery price backfill complete: {} row(s) written, {} of {} candidate symbol(s) still short of {} qualifying sessions after walking {} day(s) (max {})",
            rowsWritten, stillNeeded.size(), candidates.size(), TARGET_ROWS, daysWalked, MAX_LOOKBACK_DAYS
        );
    }

    /** How many of a symbol's captured trade dates fall strictly before its own target date - the real "sessions before the deal that needs them" count, not a raw total. */
    private static long qualifyingCount(Set<LocalDate> dates, LocalDate targetBeforeDate) {
        return dates.stream().filter(d -> d.isBefore(targetBeforeDate)).count();
    }

    /** Same full-row column order as {@link BhavdataParser}/{@link HistoricalBackfillService}. */
    private static RawDeliveryRow toRawDeliveryRow(String[] fields) {
        return new RawDeliveryRow(
            fields[0].trim(), fields[1].trim(), fields[2].trim(),
            fields[4].trim(), fields[5].trim(), fields[6].trim(), fields[8].trim(),
            fields[10].trim(), fields[11].trim(), fields[14].trim()
        );
    }
}
