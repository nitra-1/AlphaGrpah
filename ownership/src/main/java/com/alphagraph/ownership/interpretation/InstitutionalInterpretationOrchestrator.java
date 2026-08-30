package com.alphagraph.ownership.interpretation;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Drives the full chain for every symbol in {@link InterpretationDrivingSetReader}'s driving set:
 * participant flow -> event structure -> institutional state -> (if directional) anchor
 * resolution + Discovery Confirmation -> final interpretation + reason codes. Per-symbol
 * try/catch, log and continue, matching {@code decision.engine.DecisionScoringOrchestrator}'s
 * established pattern.
 */
@Component
class InstitutionalInterpretationOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(InstitutionalInterpretationOrchestrator.class);
    static final int RULE_VERSION = 1;
    private static final int WINDOW_DAYS = 20;
    /** Comfortably past the 5-session freeze point, so a freshly-formed anchor doesn't need repeated re-fetches as it matures. */
    private static final int POST_ANCHOR_SESSION_LIMIT = 10;
    private static final int BASELINE_SESSION_COUNT = 20;

    private final UnresolvedParticipantBackfillService backfillService;
    private final InterpretationDrivingSetReader drivingSetReader;
    private final InterpretationDealReader dealReader;
    private final PriorInterpretationReader priorInterpretationReader;
    private final ParticipantFlowAnalyzer flowAnalyzer;
    private final DealEventStructureEngine eventStructureEngine;
    private final ConfirmationAnchorResolver anchorResolver;
    private final DiscoveredPriceHistoryReader priceHistoryReader;
    private final DiscoveryConfirmationEngine confirmationEngine;
    private final InstitutionalInterpretationEngine interpretationEngine;
    private final InstitutionalInterpretationWriter writer;
    private final Clock clock;

    @Autowired
    InstitutionalInterpretationOrchestrator(
        UnresolvedParticipantBackfillService backfillService, InterpretationDrivingSetReader drivingSetReader,
        InterpretationDealReader dealReader, PriorInterpretationReader priorInterpretationReader,
        ParticipantFlowAnalyzer flowAnalyzer, DealEventStructureEngine eventStructureEngine,
        ConfirmationAnchorResolver anchorResolver, DiscoveredPriceHistoryReader priceHistoryReader,
        DiscoveryConfirmationEngine confirmationEngine, InstitutionalInterpretationEngine interpretationEngine,
        InstitutionalInterpretationWriter writer
    ) {
        this(
            backfillService, drivingSetReader, dealReader, priorInterpretationReader, flowAnalyzer, eventStructureEngine,
            anchorResolver, priceHistoryReader, confirmationEngine, interpretationEngine, writer,
            Clock.system(ZoneId.of("Asia/Kolkata"))
        );
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    InstitutionalInterpretationOrchestrator(
        UnresolvedParticipantBackfillService backfillService, InterpretationDrivingSetReader drivingSetReader,
        InterpretationDealReader dealReader, PriorInterpretationReader priorInterpretationReader,
        ParticipantFlowAnalyzer flowAnalyzer, DealEventStructureEngine eventStructureEngine,
        ConfirmationAnchorResolver anchorResolver, DiscoveredPriceHistoryReader priceHistoryReader,
        DiscoveryConfirmationEngine confirmationEngine, InstitutionalInterpretationEngine interpretationEngine,
        InstitutionalInterpretationWriter writer, Clock clock
    ) {
        this.backfillService = backfillService;
        this.drivingSetReader = drivingSetReader;
        this.dealReader = dealReader;
        this.priorInterpretationReader = priorInterpretationReader;
        this.flowAnalyzer = flowAnalyzer;
        this.eventStructureEngine = eventStructureEngine;
        this.anchorResolver = anchorResolver;
        this.priceHistoryReader = priceHistoryReader;
        this.confirmationEngine = confirmationEngine;
        this.interpretationEngine = interpretationEngine;
        this.writer = writer;
        this.clock = clock;
    }

    void run() {
        backfillService.resolveOutstanding();

        List<String> symbols = drivingSetReader.findSymbolsToProcess();
        if (symbols.isEmpty()) {
            log.info("Institutional interpretation: no symbols to process.");
            return;
        }

        LocalDate asOfDate = LocalDate.now(clock);
        int processed = 0;
        int skipped = 0;
        int failed = 0;
        for (String symbol : symbols) {
            try {
                if (processSymbol(symbol, asOfDate)) {
                    processed++;
                } else {
                    skipped++;
                }
            } catch (Exception e) {
                failed++;
                log.error("Failed to interpret {}: {}", symbol, e.getMessage(), e);
            }
        }
        log.info(
            "Institutional interpretation complete: {} processed, {} skipped, {} failed (of {})",
            processed, skipped, failed, symbols.size()
        );
    }

    private boolean processSymbol(String symbol, LocalDate asOfDate) {
        LocalDate windowStart = asOfDate.minusDays(WINDOW_DAYS - 1L);
        List<WindowDealRow> windowRows = dealReader.findWindowDeals(symbol, windowStart, asOfDate);
        if (windowRows.isEmpty()) {
            return false;
        }

        List<ParticipantDealActivity> windowActivity = toActivity(windowRows);
        SymbolFlowSummary flowSummary = flowAnalyzer.analyze(windowActivity);

        MaterialityLevel maxMateriality = windowRows.stream()
            .map(WindowDealRow::materialityLevel).max(Comparator.naturalOrder()).orElse(MaterialityLevel.LOW);
        Double maxMaterialityScore = windowRows.stream()
            .map(WindowDealRow::materialityScore).filter(Objects::nonNull).max(Comparator.naturalOrder()).orElse(null);
        String latestReportedFlowState = windowRows.stream()
            .filter(r -> r.reportedFlowState() != null)
            .max(Comparator.comparing(WindowDealRow::dealDate))
            .map(WindowDealRow::reportedFlowState).orElse(null);

        EventStructure eventStructure = eventStructureEngine.decide(flowSummary, maxMateriality);
        InstitutionalState institutionalState = InstitutionalInterpretationEngine.institutionalStateFor(eventStructure);

        Optional<PriorInterpretation> prior = priorInterpretationReader.findLatest(symbol);
        InstitutionalState priorState = prior.map(PriorInterpretation::institutionalState).orElse(null);
        LocalDate priorAnchor = prior.map(PriorInterpretation::eventAnchorDate).orElse(null);

        List<AnchorCandidateDeal> anchorCandidates = windowRows.stream()
            .map(r -> new AnchorCandidateDeal(r.participantId(), r.dealDate(), r.buySell(), r.materialityLevel(), r.quantity(), r.price(), r.value()))
            .toList();

        LocalDate anchorDate = anchorResolver.resolve(institutionalState, priorState, priorAnchor, anchorCandidates).orElse(null);

        DiscoveryConfirmationResult confirmation;
        List<DiscoveredPriceRow> preAnchorBaseline = List.of();
        if (anchorDate != null) {
            List<DiscoveredPriceRow> postAnchorSessions = priceHistoryReader.findSessionsAfter(symbol, anchorDate, POST_ANCHOR_SESSION_LIMIT);
            preAnchorBaseline = priceHistoryReader.findSessionsBefore(symbol, anchorDate, BASELINE_SESSION_COUNT);
            LocalDate lastPostAnchorDate = postAnchorSessions.isEmpty()
                ? anchorDate
                : postAnchorSessions.get(postAnchorSessions.size() - 1).tradeDate();
            List<ParticipantDealActivity> postAnchorActivity = toActivity(
                dealReader.findWindowDeals(symbol, anchorDate.plusDays(1), lastPostAnchorDate)
            );
            confirmation = confirmationEngine.evaluate(
                institutionalState, anchorDate, anchorCandidates, postAnchorSessions, preAnchorBaseline, postAnchorActivity
            );
        } else {
            confirmation = DiscoveryConfirmationResult.notApplicable();
        }

        boolean allDealsInWindowScored = windowRows.stream().allMatch(r -> r.materialityScore() != null);

        InstitutionalInterpretationInput input = new InstitutionalInterpretationInput(
            symbol, asOfDate, flowSummary, eventStructure, institutionalState, maxMateriality, maxMaterialityScore,
            latestReportedFlowState, anchorCandidates, confirmation, preAnchorBaseline, allDealsInWindowScored, RULE_VERSION
        );
        writer.write(interpretationEngine.assemble(input));
        return true;
    }

    private static List<ParticipantDealActivity> toActivity(List<WindowDealRow> rows) {
        return rows.stream()
            .map(r -> new ParticipantDealActivity(
                r.participantId(), r.canonicalName(), r.participantType(), r.participantConfidence(),
                r.buySell(), r.value(), r.dealDate()
            ))
            .toList();
    }
}
