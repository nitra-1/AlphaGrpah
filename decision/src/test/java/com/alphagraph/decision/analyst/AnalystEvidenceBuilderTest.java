package com.alphagraph.decision.analyst;

import com.alphagraph.corporate.api.CorporateEvent;
import com.alphagraph.corporate.api.CorporateRating;
import com.alphagraph.corporate.api.CorporateScore;
import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.EventType;
import com.alphagraph.corporate.api.GuidanceTrend;
import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import com.alphagraph.corporate.api.ManagementCredibility;
import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.NewsCatalystTrend;
import com.alphagraph.corporate.api.OrderBookEntry;
import com.alphagraph.corporate.api.OrderLifecycleStage;
import com.alphagraph.corporate.api.OrderBookSnapshot;
import com.alphagraph.corporate.api.OrderQuality;
import com.alphagraph.corporate.api.RelationshipEdge;
import com.alphagraph.corporate.api.RelationshipType;
import com.alphagraph.corporate.api.RevenueImpact;
import com.alphagraph.corporate.commentary.ManagementObservationReader;
import com.alphagraph.corporate.commentary.ManagementSnapshotReader;
import com.alphagraph.corporate.events.CorporateEventReader;
import com.alphagraph.corporate.news.NewsCatalystSnapshotReader;
import com.alphagraph.corporate.orderbook.OrderBookLedgerReader;
import com.alphagraph.corporate.orderbook.OrderBookSnapshotReader;
import com.alphagraph.corporate.relationships.EntityReader;
import com.alphagraph.corporate.relationships.RelationshipReader;
import com.alphagraph.corporate.signal.CorporateScoreReader;
import com.alphagraph.decision.api.DecisionRating;
import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionScoreReader;
import com.alphagraph.sector.api.Leadership;
import com.alphagraph.sector.api.MoneyFlow;
import com.alphagraph.sector.api.Rotation;
import com.alphagraph.sector.api.SectorMomentum;
import com.alphagraph.sector.api.SectorScore;
import com.alphagraph.sector.engine.SectorScoreReader;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AnalystEvidenceBuilderTest {

    private final CorporateScoreReader corporateScoreReader = mock(CorporateScoreReader.class);
    private final OrderBookSnapshotReader orderBookSnapshotReader = mock(OrderBookSnapshotReader.class);
    private final OrderBookLedgerReader orderBookLedgerReader = mock(OrderBookLedgerReader.class);
    private final ManagementSnapshotReader managementSnapshotReader = mock(ManagementSnapshotReader.class);
    private final ManagementObservationReader managementObservationReader = mock(ManagementObservationReader.class);
    private final NewsCatalystSnapshotReader newsCatalystSnapshotReader = mock(NewsCatalystSnapshotReader.class);
    private final CorporateEventReader corporateEventReader = mock(CorporateEventReader.class);
    private final SectorScoreReader sectorScoreReader = mock(SectorScoreReader.class);
    private final RelationshipReader relationshipReader = mock(RelationshipReader.class);
    private final EntityReader entityReader = mock(EntityReader.class);
    private final DecisionScoreReader decisionScoreReader = mock(DecisionScoreReader.class);

    private final AnalystEvidenceBuilder builder = new AnalystEvidenceBuilder(
        corporateScoreReader, orderBookSnapshotReader, orderBookLedgerReader, managementSnapshotReader,
        managementObservationReader, newsCatalystSnapshotReader, corporateEventReader, sectorScoreReader,
        relationshipReader, entityReader, decisionScoreReader
    );

    private final UUID instrumentId = UUID.randomUUID();

    @Test
    void emptyEverywhereYieldsNoFacts() {
        stubEmptyDefaults();

        assertThat(builder.buildScoreEvidence(instrumentId)).isEmpty();
    }

    @Test
    void scoreImprovementBetweenTwoDaysProducesScoreChangeFact() {
        stubEmptyDefaults();
        CorporateScore today = score(70.0, CorporateRating.STRONG, LocalDate.of(2026, 6, 2));
        CorporateScore yesterday = score(50.0, CorporateRating.NEUTRAL, LocalDate.of(2026, 6, 1));
        when(corporateScoreReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).contains("SCORE_CHANGE");
        assertThat(facts.stream().filter(f -> f.factType().equals("SCORE_CHANGE")).findFirst().orElseThrow().description())
            .contains("improved").contains("50.0").contains("70.0");
    }

    @Test
    void singleScoreDayProducesCurrentFactNotChangeFact() {
        stubEmptyDefaults();
        when(corporateScoreReader.findHistory(instrumentId)).thenReturn(List.of(score(60.0, CorporateRating.NEUTRAL, LocalDate.of(2026, 6, 1))));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).contains("SCORE_CURRENT").doesNotContain("SCORE_CHANGE");
    }

    @Test
    void orderBookAtMaxOfHistoryIsFlaggedAsHighestEver() {
        stubEmptyDefaults();
        OrderBookSnapshot latest = orderBookSnapshot(2800.0, LocalDate.of(2026, 6, 2));
        OrderBookSnapshot prior = orderBookSnapshot(1000.0, LocalDate.of(2026, 6, 1));
        when(orderBookSnapshotReader.findHistory(instrumentId)).thenReturn(List.of(latest, prior));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).contains("ORDER_BOOK_MILESTONE");
        assertThat(facts.stream().filter(f -> f.factType().equals("ORDER_BOOK_MILESTONE")).findFirst().orElseThrow().description())
            .contains("2800").contains("highest-ever");
    }

    @Test
    void orderBookBelowHistoricalMaxIsNotFlaggedAsHighestEver() {
        stubEmptyDefaults();
        OrderBookSnapshot latest = orderBookSnapshot(500.0, LocalDate.of(2026, 6, 2));
        OrderBookSnapshot prior = orderBookSnapshot(1000.0, LocalDate.of(2026, 6, 1));
        when(orderBookSnapshotReader.findHistory(instrumentId)).thenReturn(List.of(latest, prior));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).contains("ORDER_BOOK_CURRENT").doesNotContain("ORDER_BOOK_MILESTONE");
    }

    @Test
    void singleOrderBookSnapshotIsNeverFlaggedAsHighestEver() {
        stubEmptyDefaults();
        when(orderBookSnapshotReader.findHistory(instrumentId)).thenReturn(List.of(orderBookSnapshot(2800.0, LocalDate.of(2026, 6, 1))));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).contains("ORDER_BOOK_CURRENT").doesNotContain("ORDER_BOOK_MILESTONE");
    }

    @Test
    void newOrderLedgerEntryResolvesCustomerNameAndProducesOrderWinFact() {
        stubEmptyDefaults();
        UUID customerEntityId = UUID.randomUUID();
        when(entityReader.findCanonicalName(customerEntityId)).thenReturn(Optional.of("Ministry of Defence"));
        OrderBookEntry entry = new OrderBookEntry(
            UUID.randomUUID(), UUID.randomUUID(), instrumentId, "BEL", customerEntityId, 2800.0, "Electronics",
            "2026", "2029", null, null, null, OrderLifecycleStage.TENDER_WIN, Instant.now()
        );
        when(orderBookLedgerReader.findByInstrument(instrumentId)).thenReturn(List.of(entry));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts.stream().filter(f -> f.factType().equals("ORDER_WIN")).findFirst().orElseThrow().description())
            .contains("2800").contains("Ministry of Defence").contains("TENDER_WIN");
    }

    @Test
    void growthVisibilityDeltaBelowThresholdProducesNoChangeFact() {
        stubEmptyDefaults();
        ManagementCommentarySnapshot today = managementSnapshot(72.0, GuidanceTrend.STABLE, LocalDate.of(2026, 6, 2));
        ManagementCommentarySnapshot yesterday = managementSnapshot(70.0, GuidanceTrend.STABLE, LocalDate.of(2026, 6, 1));
        when(managementSnapshotReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).doesNotContain("GROWTH_VISIBILITY_CHANGE");
    }

    @Test
    void growthVisibilityDeltaAboveThresholdProducesChangeFact() {
        stubEmptyDefaults();
        ManagementCommentarySnapshot today = managementSnapshot(75.0, GuidanceTrend.UPGRADING, LocalDate.of(2026, 6, 2));
        ManagementCommentarySnapshot yesterday = managementSnapshot(55.0, GuidanceTrend.STABLE, LocalDate.of(2026, 6, 1));
        when(managementSnapshotReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).contains("GROWTH_VISIBILITY_CHANGE", "GUIDANCE_TREND");
    }

    @Test
    void sectorRankOneIsDescribedAsStrongest() {
        stubEmptyDefaults();
        UUID sectorId = UUID.randomUUID();
        SectorScore defence = sectorScore(sectorId, "Defence", 90.0);
        SectorScore other = sectorScore(UUID.randomUUID(), "IT", 60.0);
        when(sectorScoreReader.findLatestForInstrument(instrumentId)).thenReturn(Optional.of(defence));
        when(sectorScoreReader.findAllLatest()).thenReturn(List.of(other, defence));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts.stream().filter(f -> f.factType().equals("SECTOR_STANDING")).findFirst().orElseThrow().description())
            .contains("Defence").contains("strongest");
    }

    @Test
    void sectorRankTwoIsDescribedWithItsRank() {
        stubEmptyDefaults();
        UUID sectorId = UUID.randomUUID();
        SectorScore itSector = sectorScore(sectorId, "IT", 60.0);
        SectorScore defence = sectorScore(UUID.randomUUID(), "Defence", 90.0);
        when(sectorScoreReader.findLatestForInstrument(instrumentId)).thenReturn(Optional.of(itSector));
        when(sectorScoreReader.findAllLatest()).thenReturn(List.of(defence, itSector));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts.stream().filter(f -> f.factType().equals("SECTOR_STANDING")).findFirst().orElseThrow().description())
            .contains("#2 of 2");
    }

    @Test
    void negativeNewsCatalystTrendProducesNoFact() {
        stubEmptyDefaults();
        when(newsCatalystSnapshotReader.findLatest(instrumentId)).thenReturn(Optional.of(
            new NewsCatalystSnapshot(instrumentId, "BEL", LocalDate.of(2026, 6, 1), 30.0, NewsCatalystTrend.NEGATIVE, 2, 60.0, 1, Instant.now())
        ));

        assertThat(builder.buildScoreEvidence(instrumentId)).extracting(EvidenceFact::factType).doesNotContain("NEWS_CATALYST");
    }

    @Test
    void positiveCorporateEventProducesFactNegativeDoesNot() {
        stubEmptyDefaults();
        CorporateEvent positive = event(EventSignal.POSITIVE, EventType.LARGE_ORDER);
        CorporateEvent negative = event(EventSignal.NEGATIVE, EventType.PROMOTER_SELLING);
        when(corporateEventReader.findRecentByInstrument(instrumentId, 90)).thenReturn(List.of(positive, negative));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        List<String> eventDescriptions = facts.stream().filter(f -> f.factType().equals("CORPORATE_EVENT")).map(EvidenceFact::description).toList();
        assertThat(eventDescriptions).hasSize(1);
        assertThat(eventDescriptions.get(0)).contains("LARGE_ORDER");
    }

    @Test
    void beneficiaryOfEdgeProducesGraphFact() {
        stubEmptyDefaults();
        UUID entityId = UUID.randomUUID();
        when(entityReader.findByLinkedInstrument(instrumentId)).thenReturn(Optional.of(entityId));
        when(relationshipReader.findOutgoing(entityId)).thenReturn(List.of(
            new RelationshipEdge(entityId, "BEL", RelationshipType.BENEFICIARY_OF, UUID.randomUUID(), "Defence Indigenisation", 90.0)
        ));

        List<EvidenceFact> facts = builder.buildScoreEvidence(instrumentId);

        assertThat(facts.stream().filter(f -> f.factType().equals("GRAPH_RELATIONSHIP")).findFirst().orElseThrow().description())
            .isEqualTo("Beneficiary of Defence Indigenisation");
    }

    @Test
    void emptyDecisionHistoryYieldsNoRankFacts() {
        stubEmptyDefaults();
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of());

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).doesNotContain("RANK_CHANGE", "RANK_CURRENT");
    }

    @Test
    void singleDecisionScoreDayProducesRankCurrentFactNotChangeFact() {
        stubEmptyDefaults();
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of(
            decisionScore(LocalDate.of(2026, 6, 1), 5, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0)
        ));

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).contains("RANK_CURRENT").doesNotContain("RANK_CHANGE");
    }

    @Test
    void rankImprovementProducesRankChangeFactWithCorrectDirection() {
        stubEmptyDefaults();
        DecisionScore today = decisionScore(LocalDate.of(2026, 6, 2), 5, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        DecisionScore yesterday = decisionScore(LocalDate.of(2026, 6, 1), 18, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts.stream().filter(f -> f.factType().equals("RANK_CHANGE")).findFirst().orElseThrow().description())
            .contains("improved").contains("18").contains("5").contains("by 13");
    }

    @Test
    void rankDeclineProducesRankChangeFactWithCorrectDirection() {
        stubEmptyDefaults();
        DecisionScore today = decisionScore(LocalDate.of(2026, 6, 2), 18, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        DecisionScore yesterday = decisionScore(LocalDate.of(2026, 6, 1), 5, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts.stream().filter(f -> f.factType().equals("RANK_CHANGE")).findFirst().orElseThrow().description())
            .contains("declined").contains("5").contains("18").contains("by 13");
    }

    @Test
    void unchangedRankProducesNoRankChangeFact() {
        stubEmptyDefaults();
        DecisionScore today = decisionScore(LocalDate.of(2026, 6, 2), 5, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        DecisionScore yesterday = decisionScore(LocalDate.of(2026, 6, 1), 5, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).doesNotContain("RANK_CHANGE");
    }

    @Test
    void domainScoreDeltaAtOrAboveThresholdProducesAChangeFactPerDomain() {
        stubEmptyDefaults();
        DecisionScore today = decisionScore(LocalDate.of(2026, 6, 2), 5, 90.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        DecisionScore yesterday = decisionScore(LocalDate.of(2026, 6, 1), 5, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts.stream().filter(f -> f.factType().equals("TECHNICAL_SCORE_CHANGE")).findFirst().orElseThrow().description())
            .contains("improved").contains("80.0").contains("90.0");
        assertThat(facts).extracting(EvidenceFact::factType).doesNotContain("FUNDAMENTAL_SCORE_CHANGE");
    }

    @Test
    void domainScoreDeltaBelowThresholdProducesNoChangeFact() {
        stubEmptyDefaults();
        DecisionScore today = decisionScore(LocalDate.of(2026, 6, 2), 5, 82.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        DecisionScore yesterday = decisionScore(LocalDate.of(2026, 6, 1), 5, 80.0, 80.0, 80.0, 80.0, 80.0, 80.0);
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).doesNotContain("TECHNICAL_SCORE_CHANGE");
    }

    @Test
    void missingDomainScoreOnEitherDayIsSkippedRatherThanThrowing() {
        stubEmptyDefaults();
        DecisionScore today = decisionScore(LocalDate.of(2026, 6, 2), 5, 90.0, 80.0, 80.0, 80.0, 80.0, null);
        DecisionScore yesterday = decisionScore(LocalDate.of(2026, 6, 1), 5, 80.0, 80.0, 80.0, 80.0, 80.0, null);
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of(today, yesterday));

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).doesNotContain("CORPORATE_SCORE_CHANGE");
    }

    @Test
    void rankEvidenceAlsoIncludesTheSameCorporateContextScoreEvidenceUses() {
        stubEmptyDefaults();
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of());
        UUID sectorId = UUID.randomUUID();
        when(sectorScoreReader.findLatestForInstrument(instrumentId)).thenReturn(Optional.of(sectorScore(sectorId, "Defence", 90.0)));
        when(sectorScoreReader.findAllLatest()).thenReturn(List.of(sectorScore(sectorId, "Defence", 90.0)));

        List<EvidenceFact> facts = builder.buildRankEvidence(instrumentId);

        assertThat(facts).extracting(EvidenceFact::factType).contains("SECTOR_STANDING");
    }

    private void stubEmptyDefaults() {
        when(corporateScoreReader.findHistory(instrumentId)).thenReturn(List.of());
        when(orderBookSnapshotReader.findHistory(instrumentId)).thenReturn(List.of());
        when(orderBookLedgerReader.findByInstrument(instrumentId)).thenReturn(List.of());
        when(managementSnapshotReader.findHistory(instrumentId)).thenReturn(List.of());
        when(managementObservationReader.findByInstrument(instrumentId)).thenReturn(List.of());
        when(newsCatalystSnapshotReader.findLatest(instrumentId)).thenReturn(Optional.empty());
        when(corporateEventReader.findRecentByInstrument(instrumentId, 90)).thenReturn(List.of());
        when(sectorScoreReader.findLatestForInstrument(instrumentId)).thenReturn(Optional.empty());
        when(entityReader.findByLinkedInstrument(instrumentId)).thenReturn(Optional.empty());
        when(decisionScoreReader.findHistory(instrumentId)).thenReturn(List.of());
    }

    private DecisionScore decisionScore(
        LocalDate asOfDate, int swingRank, Double technicalScore, Double fundamentalScore,
        Double institutionalScore, Double sectorScoreValue, Double riskScoreValue, Double corporateScoreValue
    ) {
        return new DecisionScore(
            instrumentId, "BEL", asOfDate,
            80.0, DecisionRating.BUY, swingRank,
            75.0, DecisionRating.BUY, 1,
            technicalScore, fundamentalScore, institutionalScore, sectorScoreValue, riskScoreValue, corporateScoreValue,
            90.0, 1, Instant.now(),
            null, null, null, null, null, null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, null
        );
    }

    private CorporateScore score(double value, CorporateRating rating, LocalDate asOfDate) {
        return new CorporateScore(instrumentId, "BEL", asOfDate, value, rating, null, null, null, 0, 60.0, 1, Instant.now());
    }

    private OrderBookSnapshot orderBookSnapshot(double crore, LocalDate asOfDate) {
        return new OrderBookSnapshot(instrumentId, "BEL", asOfDate, crore, null, 3.0, 1, OrderQuality.GOOD, 70.0, 80.0, 1, Instant.now());
    }

    private ManagementCommentarySnapshot managementSnapshot(double growthVisibilityScore, GuidanceTrend trend, LocalDate asOfDate) {
        return new ManagementCommentarySnapshot(instrumentId, "BEL", asOfDate, growthVisibilityScore, trend, ManagementCredibility.HIGH, 70.0, 1, Instant.now());
    }

    private SectorScore sectorScore(UUID sectorId, String name, double sectorScoreValue) {
        return new SectorScore(
            sectorId, name, LocalDate.of(2026, 6, 1), Leadership.INCREASING, SectorMomentum.STRONG, Rotation.POSITIVE, MoneyFlow.STRONG,
            sectorScoreValue, 80.0, null, null, null, null, null, 5, 1, Instant.now()
        );
    }

    private CorporateEvent event(EventSignal signal, EventType type) {
        return new CorporateEvent(
            UUID.randomUUID(), UUID.randomUUID(), instrumentId, "BEL", type, "Category", "Summary",
            85.0, null, RevenueImpact.HIGH, signal, 1, Instant.now()
        );
    }
}
