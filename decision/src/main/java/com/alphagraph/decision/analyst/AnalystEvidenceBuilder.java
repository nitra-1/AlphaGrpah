package com.alphagraph.decision.analyst;

import com.alphagraph.corporate.api.CorporateEvent;
import com.alphagraph.corporate.api.CorporateScore;
import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.GuidanceDirection;
import com.alphagraph.corporate.api.GuidanceTrend;
import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import com.alphagraph.corporate.api.ManagementObservation;
import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.NewsCatalystTrend;
import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderBookSnapshot;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.RelationshipEdge;
import com.alphagraph.corporate.api.RelationshipType;
import com.alphagraph.corporate.commentary.ManagementObservationReader;
import com.alphagraph.corporate.commentary.ManagementSnapshotReader;
import com.alphagraph.corporate.events.CorporateEventReader;
import com.alphagraph.corporate.news.NewsCatalystSnapshotReader;
import com.alphagraph.corporate.orderbook.OrderBookLedgerReader;
import com.alphagraph.corporate.orderbook.OrderBookSnapshotReader;
import com.alphagraph.corporate.relationships.EntityReader;
import com.alphagraph.corporate.relationships.RelationshipReader;
import com.alphagraph.corporate.signal.CorporateScoreReader;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionScoreReader;
import com.alphagraph.sector.api.SectorScore;
import com.alphagraph.sector.engine.SectorScoreReader;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Gathers and computes every fact {@link AiAnalystClient} is allowed to reference - all the
 * arithmetic (deltas, "highest ever", sector rank) happens here, deterministically, before the
 * LLM is ever called. Per the user's explicit design: "AI explains, it never calculates."
 *
 * <p>Lives in {@code decision}, not {@code intelligence} where Module 2.9 originally built it:
 * at the time, bridging corporate signals and sector standing required {@code intelligence}
 * (domain modules never depend on each other directly, docs/001_System_Architecture.md §4). Once
 * Module 3.1 gave {@code decision} its own direct dependency on every domain module it needed
 * (Rule 4 has always named {@code decision} alongside {@code intelligence} as allowed to), the
 * bridging justification for living in {@code intelligence} no longer applied - and
 * decision/package-info.java had claimed ownership of "AI analyst" since Module 0.3's original
 * scaffold. Relocated here in Module 3.7 when extending it to explain Rank changes required
 * {@code decision.engine.DecisionScoreReader}, which {@code intelligence} cannot depend on
 * (decision already depends on intelligence, so the reverse would be a circular dependency).
 */
@Component
class AnalystEvidenceBuilder {

    private static final int EVENT_LOOKBACK_DAYS = 90;
    private static final double MEANINGFUL_SCORE_DELTA = 5.0;

    private final CorporateScoreReader corporateScoreReader;
    private final OrderBookSnapshotReader orderBookSnapshotReader;
    private final OrderBookLedgerReader orderBookLedgerReader;
    private final ManagementSnapshotReader managementSnapshotReader;
    private final ManagementObservationReader managementObservationReader;
    private final NewsCatalystSnapshotReader newsCatalystSnapshotReader;
    private final CorporateEventReader corporateEventReader;
    private final SectorScoreReader sectorScoreReader;
    private final RelationshipReader relationshipReader;
    private final EntityReader entityReader;
    private final DecisionScoreReader decisionScoreReader;

    AnalystEvidenceBuilder(
        CorporateScoreReader corporateScoreReader, OrderBookSnapshotReader orderBookSnapshotReader,
        OrderBookLedgerReader orderBookLedgerReader, ManagementSnapshotReader managementSnapshotReader,
        ManagementObservationReader managementObservationReader, NewsCatalystSnapshotReader newsCatalystSnapshotReader,
        CorporateEventReader corporateEventReader, SectorScoreReader sectorScoreReader,
        RelationshipReader relationshipReader, EntityReader entityReader, DecisionScoreReader decisionScoreReader
    ) {
        this.corporateScoreReader = corporateScoreReader;
        this.orderBookSnapshotReader = orderBookSnapshotReader;
        this.orderBookLedgerReader = orderBookLedgerReader;
        this.managementSnapshotReader = managementSnapshotReader;
        this.managementObservationReader = managementObservationReader;
        this.newsCatalystSnapshotReader = newsCatalystSnapshotReader;
        this.corporateEventReader = corporateEventReader;
        this.sectorScoreReader = sectorScoreReader;
        this.relationshipReader = relationshipReader;
        this.entityReader = entityReader;
        this.decisionScoreReader = decisionScoreReader;
    }

    /** Explains a Corporate Score change (Module 2.9's original capability). */
    List<EvidenceFact> buildScoreEvidence(UUID instrumentId) {
        List<EvidenceFact> facts = new ArrayList<>();
        addScoreChangeFact(facts, instrumentId);
        addOrderBookFacts(facts, instrumentId);
        addManagementFacts(facts, instrumentId);
        addSectorFact(facts, instrumentId);
        addNewsCatalystFact(facts, instrumentId);
        addCorporateEventFacts(facts, instrumentId);
        addGraphFacts(facts, instrumentId);
        return facts;
    }

    private void addScoreChangeFact(List<EvidenceFact> facts, UUID instrumentId) {
        List<CorporateScore> history = corporateScoreReader.findHistory(instrumentId);
        if (history.isEmpty()) {
            return;
        }
        CorporateScore latest = history.get(0);
        if (history.size() >= 2) {
            CorporateScore previous = history.get(1);
            double delta = latest.corporateScore() - previous.corporateScore();
            String direction = delta > 0 ? "improved" : delta < 0 ? "declined" : "held steady";
            facts.add(new EvidenceFact("SCORE_CHANGE", "Corporate Score %s from %.1f (%s) to %.1f (%s) since %s".formatted(
                direction, previous.corporateScore(), previous.corporateRating(), latest.corporateScore(), latest.corporateRating(), previous.asOfDate()
            )));
        } else {
            facts.add(new EvidenceFact("SCORE_CURRENT", "Corporate Score is currently %.1f (%s) - first score on record, no prior value to compare against".formatted(
                latest.corporateScore(), latest.corporateRating()
            )));
        }
    }

    /**
     * Explains a Swing Rank change (Module 3.7) - the roadmap's own worked example ("Why moved
     * from Rank 18 to Rank 5?"). Rank-specific facts (the rank move itself, then which of the six
     * domain scores moved most) come first, followed by the same corporate-side context
     * {@link #buildScoreEvidence} already surfaces - those facts still explain WHY the underlying
     * scores moved, they just weren't previously connected to a Rank because no Rank existed
     * before Module 3.1.
     */
    List<EvidenceFact> buildRankEvidence(UUID instrumentId) {
        List<EvidenceFact> facts = new ArrayList<>();
        addRankChangeFacts(facts, instrumentId);
        addOrderBookFacts(facts, instrumentId);
        addManagementFacts(facts, instrumentId);
        addSectorFact(facts, instrumentId);
        addNewsCatalystFact(facts, instrumentId);
        addCorporateEventFacts(facts, instrumentId);
        addGraphFacts(facts, instrumentId);
        return facts;
    }

    private void addRankChangeFacts(List<EvidenceFact> facts, UUID instrumentId) {
        List<DecisionScore> history = decisionScoreReader.findHistory(instrumentId);
        if (history.isEmpty()) {
            return;
        }
        DecisionScore latest = history.get(0);
        if (history.size() < 2) {
            facts.add(new EvidenceFact("RANK_CURRENT", "Swing Rank is currently %d (Swing Score %.2f, %s) - first score on record, no prior value to compare against".formatted(
                latest.swingRank(), latest.swingScore(), latest.swingRating()
            )));
            return;
        }

        DecisionScore previous = history.get(1);
        if (latest.swingRank() != null && previous.swingRank() != null && !latest.swingRank().equals(previous.swingRank())) {
            int delta = previous.swingRank() - latest.swingRank();
            String direction = delta > 0 ? "improved" : "declined";
            facts.add(new EvidenceFact("RANK_CHANGE", "Swing Rank %s from %d to %d (%s by %d) since %s".formatted(
                direction, previous.swingRank(), latest.swingRank(), direction, Math.abs(delta), previous.asOfDate()
            )));
        }
        addDomainDeltaFact(facts, "Technical", previous.technicalScore(), latest.technicalScore());
        addDomainDeltaFact(facts, "Fundamental", previous.fundamentalScore(), latest.fundamentalScore());
        addDomainDeltaFact(facts, "Institutional", previous.institutionalScore(), latest.institutionalScore());
        addDomainDeltaFact(facts, "Sector", previous.sectorScore(), latest.sectorScore());
        addDomainDeltaFact(facts, "Risk", previous.riskScore(), latest.riskScore());
        addDomainDeltaFact(facts, "Corporate", previous.corporateScore(), latest.corporateScore());
    }

    private void addDomainDeltaFact(List<EvidenceFact> facts, String domainName, Double previousScore, Double latestScore) {
        if (previousScore == null || latestScore == null) {
            return;
        }
        double delta = latestScore - previousScore;
        if (Math.abs(delta) < MEANINGFUL_SCORE_DELTA) {
            return;
        }
        String direction = delta > 0 ? "improved" : "declined";
        facts.add(new EvidenceFact(domainName.toUpperCase() + "_SCORE_CHANGE", "%s Score %s from %.1f to %.1f".formatted(
            domainName, direction, previousScore, latestScore
        )));
    }

    private void addOrderBookFacts(List<EvidenceFact> facts, UUID instrumentId) {
        List<OrderBookSnapshot> history = orderBookSnapshotReader.findHistory(instrumentId);
        if (!history.isEmpty()) {
            OrderBookSnapshot latest = history.get(0);
            double maxEver = history.stream().mapToDouble(OrderBookSnapshot::currentOrderBookCrore).max().orElse(latest.currentOrderBookCrore());
            boolean isHighestEver = latest.currentOrderBookCrore() >= maxEver && history.size() > 1;
            if (isHighestEver) {
                facts.add(new EvidenceFact("ORDER_BOOK_MILESTONE", "Order book is now at its highest-ever level of Rs %.0f Cr (%s quality)".formatted(
                    latest.currentOrderBookCrore(), latest.orderQuality()
                )));
            } else {
                facts.add(new EvidenceFact("ORDER_BOOK_CURRENT", "Current order book: Rs %.0f Cr (%s quality)".formatted(
                    latest.currentOrderBookCrore(), latest.orderQuality()
                )));
            }
            if (latest.orderBookGrowthPct() != null && latest.orderBookGrowthPct() > 0) {
                facts.add(new EvidenceFact("ORDER_BOOK_GROWTH", "Order book grew %.1f%% since the prior snapshot".formatted(latest.orderBookGrowthPct())));
            }
        }

        orderBookLedgerReader.findByInstrument(instrumentId).stream()
            .filter(e -> e.lifecycleStage() == OrderLifecycleStage.NEW_ORDER || e.lifecycleStage() == OrderLifecycleStage.TENDER_WIN)
            .sorted(Comparator.comparing(OrderBookEntry::detectedAt).reversed())
            .limit(2)
            .forEach(entry -> {
                String customerName = entry.customerEntityId() == null
                    ? "an undisclosed customer" : entityReader.findCanonicalName(entry.customerEntityId()).orElse("an undisclosed customer");
                String valueText = entry.orderValueCrore() == null ? "A new order" : "A Rs %.0f Cr order".formatted(entry.orderValueCrore());
                facts.add(new EvidenceFact("ORDER_WIN", "%s from %s (%s)".formatted(valueText, customerName, entry.lifecycleStage())));
            });
    }

    private void addManagementFacts(List<EvidenceFact> facts, UUID instrumentId) {
        List<ManagementCommentarySnapshot> history = managementSnapshotReader.findHistory(instrumentId);
        if (!history.isEmpty()) {
            ManagementCommentarySnapshot latest = history.get(0);
            if (history.size() >= 2) {
                double previousScore = history.get(1).growthVisibilityScore();
                double delta = latest.growthVisibilityScore() - previousScore;
                if (Math.abs(delta) >= MEANINGFUL_SCORE_DELTA) {
                    String direction = delta > 0 ? "improved" : "declined";
                    facts.add(new EvidenceFact("GROWTH_VISIBILITY_CHANGE", "Revenue growth visibility %s from %.1f to %.1f".formatted(
                        direction, previousScore, latest.growthVisibilityScore()
                    )));
                }
            }
            if (latest.guidanceTrend() == GuidanceTrend.UPGRADING) {
                facts.add(new EvidenceFact("GUIDANCE_TREND", "Management guidance trend is UPGRADING (credibility: %s)".formatted(latest.managementCredibility())));
            }
        }

        managementObservationReader.findByInstrument(instrumentId).stream()
            .filter(o -> o.direction() == GuidanceDirection.POSITIVE && "revenueguidance".equalsIgnoreCase(normalizedMetricType(o)))
            .limit(1)
            .forEach(o -> {
                String period = o.guidancePeriod() == null || o.guidancePeriod().isBlank() ? "" : " for " + o.guidancePeriod();
                facts.add(new EvidenceFact("GUIDANCE_STATEMENT", "Management guidance: %s%s (commitment: %s)".formatted(
                    o.guidanceValue(), period, o.commitmentLevel()
                )));
            });
    }

    private static String normalizedMetricType(ManagementObservation observation) {
        return observation.metricType() == null ? "" : observation.metricType().replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
    }

    private void addSectorFact(List<EvidenceFact> facts, UUID instrumentId) {
        Optional<SectorScore> instrumentSector = sectorScoreReader.findLatestForInstrument(instrumentId);
        if (instrumentSector.isEmpty()) {
            return;
        }
        List<SectorScore> ranked = sectorScoreReader.findAllLatest().stream()
            .sorted(Comparator.comparingDouble(SectorScore::sectorScore).reversed())
            .toList();

        int rank = 1;
        for (SectorScore s : ranked) {
            if (s.sectorId().equals(instrumentSector.get().sectorId())) {
                break;
            }
            rank++;
        }
        String standing = rank == 1 ? "remains the strongest sector" : "ranks #%d of %d sectors".formatted(rank, ranked.size());
        facts.add(new EvidenceFact("SECTOR_STANDING", "%s %s (Sector Score: %.1f, momentum: %s)".formatted(
            instrumentSector.get().sectorName(), standing, instrumentSector.get().sectorScore(), instrumentSector.get().momentum()
        )));
    }

    private void addNewsCatalystFact(List<EvidenceFact> facts, UUID instrumentId) {
        newsCatalystSnapshotReader.findLatest(instrumentId)
            .filter(n -> n.catalystTrend() == NewsCatalystTrend.POSITIVE && n.recentCatalystCount() > 0)
            .ifPresent(n -> facts.add(new EvidenceFact("NEWS_CATALYST", "Recent news coverage is trending positive (%d catalyst%s, catalyst score %.1f)".formatted(
                n.recentCatalystCount(), n.recentCatalystCount() == 1 ? "" : "s", n.catalystScore()
            ))));
    }

    private void addCorporateEventFacts(List<EvidenceFact> facts, UUID instrumentId) {
        corporateEventReader.findRecentByInstrument(instrumentId, EVENT_LOOKBACK_DAYS).stream()
            .filter(e -> e.signal() == EventSignal.POSITIVE)
            .limit(2)
            .forEach(e -> facts.add(new EvidenceFact("CORPORATE_EVENT", "Recent %s event (%s revenue impact)".formatted(e.eventType(), e.revenueImpact()))));
    }

    private void addGraphFacts(List<EvidenceFact> facts, UUID instrumentId) {
        entityReader.findByLinkedInstrument(instrumentId).ifPresent(entityId -> {
            List<RelationshipEdge> edges = relationshipReader.findOutgoing(entityId);
            edges.stream()
                .filter(e -> e.relationshipType() == RelationshipType.BENEFICIARY_OF || e.relationshipType() == RelationshipType.PART_OF_THEME)
                .limit(2)
                .forEach(e -> {
                    String phrase = e.relationshipType() == RelationshipType.BENEFICIARY_OF
                        ? "Beneficiary of %s".formatted(e.toEntityName())
                        : "Part of the %s theme".formatted(e.toEntityName());
                    facts.add(new EvidenceFact("GRAPH_RELATIONSHIP", phrase));
                });
        });
    }
}
