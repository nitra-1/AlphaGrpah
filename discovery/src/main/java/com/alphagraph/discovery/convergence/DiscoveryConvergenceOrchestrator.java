package com.alphagraph.discovery.convergence;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.reference.api.TrackedInstrumentSummary;
import com.alphagraph.reference.instrument.InstrumentReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Per-instrument try/catch loop, same log-and-continue convention as every Stage 2/3 orchestrator.
 * Drives off {@link InstrumentReader#listAll()} - the full tracked universe, not any one domain's
 * own subset, since Stage 4 is explicitly cross-domain (using e.g. Market's own instrument set
 * would arbitrarily omit companies where only Financial/Ownership have evidence).
 *
 * <p><b>{@code run()} uses {@code Clock.now()} as {@code as_of_date} - genuinely correct here,
 * unlike Financial Stage 2/3's own Clock-based mistake</b> - Stage 4 is explicitly "what does
 * convergence look like given everything known as of today," and every domain input it reads
 * remains independently point-in-time bounded via its own as-of query.
 *
 * <p><b>Historical backfill is refused, not silently unsafe</b> (round-3 plan correction): {@link
 * #backfill} exists so the intent is documented in code, but immediately throws. Financial Stage
 * 3's own {@code as_of_date} is the quarter-<i>end</i> date, not the publication date - a
 * historical Stage 4 replay could see Financial transformation evidence that wasn't actually
 * public yet on the replayed date, and launder that single upstream timing gap into a false
 * cross-domain convergence event that looks authoritative once persisted. This mirrors {@code
 * financial.transformation.FinancialTransformationSequenceOrchestrator}'s own precedent for the
 * identical root cause, extended to its natural dependent - not a new pattern. Fixing Financial's
 * own publication-date gap is an upstream Financial Stage 1/2/3 hardening task, out of scope here.
 */
@Component
class DiscoveryConvergenceOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryConvergenceOrchestrator.class);

    private final InstrumentReader instrumentReader;
    private final MarketSequenceReader marketSequenceReader;
    private final FinancialSequenceReader financialSequenceReader;
    private final OwnershipSequenceReader ownershipSequenceReader;
    private final SectorSequenceReader sectorSequenceReader;
    private final CapitalAllocationSequenceReader capitalAllocationSequenceReader;
    private final RiskContradictionOverlayReader riskContradictionOverlayReader;
    private final DiscoveryConvergenceRuleSetLoader ruleSetLoader;
    private final DiscoveryConvergenceEngine engine;
    private final DiscoveryConvergenceWriter writer;
    private final Clock clock;

    DiscoveryConvergenceOrchestrator(
        InstrumentReader instrumentReader, MarketSequenceReader marketSequenceReader, FinancialSequenceReader financialSequenceReader,
        OwnershipSequenceReader ownershipSequenceReader, SectorSequenceReader sectorSequenceReader,
        CapitalAllocationSequenceReader capitalAllocationSequenceReader, RiskContradictionOverlayReader riskContradictionOverlayReader,
        DiscoveryConvergenceRuleSetLoader ruleSetLoader, DiscoveryConvergenceEngine engine, DiscoveryConvergenceWriter writer
    ) {
        this.instrumentReader = instrumentReader;
        this.marketSequenceReader = marketSequenceReader;
        this.financialSequenceReader = financialSequenceReader;
        this.ownershipSequenceReader = ownershipSequenceReader;
        this.sectorSequenceReader = sectorSequenceReader;
        this.capitalAllocationSequenceReader = capitalAllocationSequenceReader;
        this.riskContradictionOverlayReader = riskContradictionOverlayReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.writer = writer;
        this.clock = Clock.system(java.time.ZoneId.of("Asia/Kolkata"));
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
                log.warn("Failed to compute discovery convergence for instrument {}: {}", instrument.id(), e.getMessage());
            }
        }

        log.info("Discovery convergence run complete: {} instruments succeeded, {} failed", succeeded, failed);
    }

    /**
     * Deliberately refuses to run - see this class's own javadoc. Exists so the intent (a
     * point-in-time historical replay) is documented in code rather than silently absent, matching
     * {@code FinancialTransformationSequenceOrchestrator.backfill()}'s own "exists but not wired to
     * any runnable job" posture for the identical underlying reason.
     */
    void backfill(LocalDate from, LocalDate to) {
        throw new UnsupportedOperationException(
            "FINANCIAL_STAGE3_NOT_POINT_IN_TIME_SAFE: Stage 4 historical backfill is refused until " +
            "Financial Stage 3's own publication-date gap is resolved upstream - see " +
            "FinancialTransformationSequenceOrchestrator's javadoc. A historical replay today could " +
            "see Financial transformation evidence that was not actually public on the replayed " +
            "date, laundering that timing gap into a false cross-domain convergence event."
        );
    }

    private void evaluateAndWrite(UUID instrumentId, String symbol, LocalDate asOfDate, RuleSet rules) {
        DomainContribution financial = engine.evaluateDomainContribution(
            ConvergenceDomain.FINANCIAL, financialSequenceReader.findActiveSequencesAsOf(instrumentId, asOfDate),
            financialSequenceReader.findReadinessAsOf(instrumentId, asOfDate), asOfDate, rules
        );
        DomainContribution ownership = engine.evaluateDomainContribution(
            ConvergenceDomain.OWNERSHIP, ownershipSequenceReader.findActiveSequencesAsOf(instrumentId, asOfDate),
            ownershipSequenceReader.findReadinessAsOf(instrumentId, asOfDate), asOfDate, rules
        );
        DomainContribution market = engine.evaluateDomainContribution(
            ConvergenceDomain.MARKET, marketSequenceReader.findActiveSequencesAsOf(instrumentId, asOfDate),
            marketSequenceReader.findReadinessAsOf(instrumentId, asOfDate), asOfDate, rules
        );
        DomainContribution sector = engine.evaluateDomainContribution(
            ConvergenceDomain.SECTOR, sectorSequenceReader.findActiveSequencesAsOf(instrumentId, asOfDate),
            sectorSequenceReader.findReadinessAsOf(instrumentId, asOfDate), asOfDate, rules
        );
        DomainContribution capitalAllocation = engine.evaluateDomainContribution(
            ConvergenceDomain.CAPITAL_ALLOCATION, capitalAllocationSequenceReader.findActiveSequencesAsOf(instrumentId, asOfDate),
            capitalAllocationSequenceReader.findReadinessAsOf(instrumentId, asOfDate), asOfDate, rules
        );

        List<DomainContribution> contributions = List.of(financial, ownership, market, sector, capitalAllocation);
        Optional<ContradictionOverlayRow> overlay = riskContradictionOverlayReader.findStateAsOf(instrumentId, asOfDate);

        ConvergenceResult result = engine.evaluateConvergence(instrumentId, symbol, asOfDate, contributions, overlay, rules);
        writer.write(result);
    }
}
