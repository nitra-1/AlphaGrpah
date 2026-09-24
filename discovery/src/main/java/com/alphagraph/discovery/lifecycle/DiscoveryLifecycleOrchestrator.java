package com.alphagraph.discovery.lifecycle;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.reference.api.TrackedInstrumentSummary;
import com.alphagraph.reference.instrument.InstrumentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every Stage 2/3/4
 * orchestrator. Drives off {@link InstrumentReader#listAll()} - the same universe Stage 4 uses,
 * not expanded to the full NSE universe yet.
 *
 * <p><b>Historical backfill is refused, not silently unsafe</b> - Stage 4's own historical backfill
 * is already refused because Financial Stage 3's {@code as_of_date} is the quarter-end date, not
 * the publication date. Stage 5 is built entirely on Stage 4's own history, so it inherits that
 * same unsafety one layer further - a historical Stage 5 replay would classify trajectories from
 * convergence snapshots that themselves might not have been point-in-time correct. {@link
 * #backfill} exists so the intent is documented in code, but immediately throws, the same "exists
 * but not wired to any runnable job" posture {@code FinancialTransformationSequenceOrchestrator}
 * and {@code DiscoveryConvergenceOrchestrator} already established for the identical root cause -
 * not re-litigated here.
 */
@Component
class DiscoveryLifecycleOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryLifecycleOrchestrator.class);
    /** Comfortably covers the 20-observation long window and the 14-day minimum span even with gappy history. */
    private static final int HISTORY_LIMIT = 120;

    private final InstrumentReader instrumentReader;
    private final ConvergenceHistoryReader convergenceHistoryReader;
    private final LifecycleSnapshotReader lifecycleSnapshotReader;
    private final DiscoveryLifecycleRuleSetLoader ruleSetLoader;
    private final DiscoveryLifecycleEngine engine;
    private final DiscoveryLifecycleWriter writer;
    private final Clock clock;

    DiscoveryLifecycleOrchestrator(
        InstrumentReader instrumentReader, ConvergenceHistoryReader convergenceHistoryReader,
        LifecycleSnapshotReader lifecycleSnapshotReader, DiscoveryLifecycleRuleSetLoader ruleSetLoader,
        DiscoveryLifecycleEngine engine, DiscoveryLifecycleWriter writer
    ) {
        this.instrumentReader = instrumentReader;
        this.convergenceHistoryReader = convergenceHistoryReader;
        this.lifecycleSnapshotReader = lifecycleSnapshotReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.writer = writer;
        this.clock = Clock.system(ZoneId.of("Asia/Kolkata"));
    }

    void run() {
        RuleSet rules = ruleSetLoader.loadActiveRules();
        List<TrackedInstrumentSummary> instruments = instrumentReader.listAll();
        LocalDate asOfDate = LocalDate.now(clock);

        int succeeded = 0;
        int failed = 0;
        for (TrackedInstrumentSummary instrument : instruments) {
            try {
                evaluateAndWrite(instrument.id(), instrument.symbol(), asOfDate, rules);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("Failed to compute lifecycle classification for instrument {}: {}", instrument.id(), e.getMessage());
            }
        }

        log.info("Lifecycle classification run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    /** Deliberately refuses to run - see this class's own javadoc. */
    void backfill(LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException(
            "STAGE4_HISTORY_NOT_POINT_IN_TIME_SAFE: Stage 5 historical backfill is refused because " +
            "it is built entirely on Stage 4's own convergence-snapshot history, and Stage 4's own " +
            "historical backfill is itself refused (Financial Stage 3's unresolved publication-date " +
            "gap) - see DiscoveryConvergenceOrchestrator's and " +
            "FinancialTransformationSequenceOrchestrator's own javadoc for the underlying reason."
        );
    }

    private void evaluateAndWrite(UUID instrumentId, String symbol, LocalDate asOfDate, RuleSet rules) {
        List<ConvergenceSnapshotRow> history = convergenceHistoryReader.findHistory(instrumentId, asOfDate, HISTORY_LIMIT);
        if (history.isEmpty()) {
            return;
        }
        Optional<LifecycleSnapshotRow> previousLifecycle = lifecycleSnapshotReader.findLatestAuthoritativeBefore(instrumentId, asOfDate);
        LifecycleResult result = engine.evaluate(instrumentId, symbol, asOfDate, history, previousLifecycle, rules);
        writer.write(result, previousLifecycle);
    }
}
