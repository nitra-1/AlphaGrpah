package com.alphagraph.api.admin;

import com.alphagraph.corporate.commentary.ManagementCommentaryScheduler;
import com.alphagraph.corporate.events.EventExtractionScheduler;
import com.alphagraph.corporate.knowledge.KnowledgeExtractionScheduler;
import com.alphagraph.corporate.news.NewsCatalystScheduler;
import com.alphagraph.corporate.orderbook.OrderBookScheduler;
import com.alphagraph.corporate.processing.DocumentProcessingScheduler;
import com.alphagraph.corporate.signal.CorporateSignalScheduler;
import com.alphagraph.corporate.transformation.CapitalAllocationScheduler;
import com.alphagraph.decision.engine.DecisionScoringScheduler;
import com.alphagraph.decision.report.DailyReportScheduler;
import com.alphagraph.financial.engine.FundamentalAnalysisScheduler;
import com.alphagraph.financial.transformation.FinancialTransformationScheduler;
import com.alphagraph.intelligence.financial.FinancialResultsBridgeScheduler;
import com.alphagraph.intelligence.institutional.InstitutionalAnalysisScheduler;
import com.alphagraph.intelligence.risk.RiskAnalysisScheduler;
import com.alphagraph.intelligence.sector.SectorAnalysisScheduler;
import com.alphagraph.intelligence.sectorcontext.SectorContextScheduler;
import com.alphagraph.intelligence.technical.TechnicalAnalysisScheduler;
import com.alphagraph.learning.outcomes.ForwardOutcomeScheduler;
import com.alphagraph.learning.snapshot.DecisionSnapshotScheduler;
import com.alphagraph.market.pricing.MarketPriceBackfillScheduler;
import com.alphagraph.market.transformation.MarketAccumulationScheduler;
import com.alphagraph.market.transformation.MarketInflectionScheduler;
import com.alphagraph.ownership.deals.DealMaterialityScoringScheduler;
import com.alphagraph.ownership.interpretation.InstitutionalInterpretationScheduler;
import com.alphagraph.ownership.pattern.XbrlEnrichmentScheduler;
import com.alphagraph.ownership.transformation.OwnershipTransformationScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JobRegistryTest {

    private final MarketPriceBackfillScheduler marketPriceBackfillScheduler = mock(MarketPriceBackfillScheduler.class);
    private final DealMaterialityScoringScheduler dealMaterialityScoringScheduler = mock(DealMaterialityScoringScheduler.class);
    private final InstitutionalInterpretationScheduler institutionalInterpretationScheduler = mock(InstitutionalInterpretationScheduler.class);
    private final DocumentProcessingScheduler documentProcessingScheduler = mock(DocumentProcessingScheduler.class);
    private final KnowledgeExtractionScheduler knowledgeExtractionScheduler = mock(KnowledgeExtractionScheduler.class);
    private final TechnicalAnalysisScheduler technicalAnalysisScheduler = mock(TechnicalAnalysisScheduler.class);
    private final FinancialResultsBridgeScheduler financialResultsBridgeScheduler = mock(FinancialResultsBridgeScheduler.class);
    private final FundamentalAnalysisScheduler fundamentalAnalysisScheduler = mock(FundamentalAnalysisScheduler.class);
    private final EventExtractionScheduler eventExtractionScheduler = mock(EventExtractionScheduler.class);
    private final InstitutionalAnalysisScheduler institutionalAnalysisScheduler = mock(InstitutionalAnalysisScheduler.class);
    private final SectorAnalysisScheduler sectorAnalysisScheduler = mock(SectorAnalysisScheduler.class);
    private final RiskAnalysisScheduler riskAnalysisScheduler = mock(RiskAnalysisScheduler.class);
    private final OrderBookScheduler orderBookScheduler = mock(OrderBookScheduler.class);
    private final ManagementCommentaryScheduler managementCommentaryScheduler = mock(ManagementCommentaryScheduler.class);
    private final NewsCatalystScheduler newsCatalystScheduler = mock(NewsCatalystScheduler.class);
    private final CorporateSignalScheduler corporateSignalScheduler = mock(CorporateSignalScheduler.class);
    private final DecisionScoringScheduler decisionScoringScheduler = mock(DecisionScoringScheduler.class);
    private final DecisionSnapshotScheduler decisionSnapshotScheduler = mock(DecisionSnapshotScheduler.class);
    private final ForwardOutcomeScheduler forwardOutcomeScheduler = mock(ForwardOutcomeScheduler.class);
    private final DailyReportScheduler dailyReportScheduler = mock(DailyReportScheduler.class);
    private final XbrlEnrichmentScheduler xbrlEnrichmentScheduler = mock(XbrlEnrichmentScheduler.class);
    private final OwnershipTransformationScheduler ownershipTransformationScheduler = mock(OwnershipTransformationScheduler.class);
    private final MarketAccumulationScheduler marketAccumulationScheduler = mock(MarketAccumulationScheduler.class);
    private final FinancialTransformationScheduler financialTransformationScheduler = mock(FinancialTransformationScheduler.class);
    private final CapitalAllocationScheduler capitalAllocationScheduler = mock(CapitalAllocationScheduler.class);
    private final SectorContextScheduler sectorContextScheduler = mock(SectorContextScheduler.class);
    private final MarketInflectionScheduler marketInflectionScheduler = mock(MarketInflectionScheduler.class);

    private final JobRegistry registry = new JobRegistry(
        marketPriceBackfillScheduler, dealMaterialityScoringScheduler, institutionalInterpretationScheduler,
        documentProcessingScheduler, knowledgeExtractionScheduler, technicalAnalysisScheduler,
        financialResultsBridgeScheduler, fundamentalAnalysisScheduler, eventExtractionScheduler,
        institutionalAnalysisScheduler, sectorAnalysisScheduler, riskAnalysisScheduler, orderBookScheduler,
        managementCommentaryScheduler, newsCatalystScheduler, corporateSignalScheduler, decisionScoringScheduler,
        decisionSnapshotScheduler, forwardOutcomeScheduler, dailyReportScheduler, xbrlEnrichmentScheduler,
        ownershipTransformationScheduler, marketAccumulationScheduler, financialTransformationScheduler,
        capitalAllocationScheduler, sectorContextScheduler, marketInflectionScheduler
    );

    private static final List<String> ALL_31_JOB_NAMES = List.of(
        "market-discovery-price-backfill", "deal-materiality-scoring", "institutional-interpretation",
        "document-processing", "knowledge-extraction", "technical-analysis", "financial-results-bridge",
        "fundamental-analysis", "corporate-event-extraction", "institutional-analysis", "sector-analysis",
        "risk-analysis", "order-book", "management-commentary", "news-catalyst", "corporate-signal",
        "decision-scoring", "decision-snapshot-archive", "forward-outcome-tracking", "daily-ai-report",
        "xbrl-shareholding-enrichment", "ownership-transformation", "market-accumulation-evidence",
        "financial-results-comparision-fetch", "capital-allocation-evidence", "sector-context-evidence",
        "market-accumulation-evidence-backfill", "financial-results-comparision-fetch-backfill",
        "ownership-transformation-backfill", "sector-context-evidence-backfill", "market-inflection"
    );

    @Test
    void containsExactlyAllThirtyOneRealJobNames() {
        for (String jobName : ALL_31_JOB_NAMES) {
            assertThat(registry.contains(jobName)).as("contains(%s)", jobName).isTrue();
        }
        assertThat(registry.contains("not-a-real-job")).isFalse();
    }

    @Test
    void triggerInvokesOnlyTheMatchingSchedulerMethod() {
        registry.trigger("knowledge-extraction");

        verify(knowledgeExtractionScheduler).runKnowledgeExtraction();
        verifyNoInteractions(
            marketPriceBackfillScheduler, dealMaterialityScoringScheduler, institutionalInterpretationScheduler,
            documentProcessingScheduler, technicalAnalysisScheduler, financialResultsBridgeScheduler,
            fundamentalAnalysisScheduler, eventExtractionScheduler, institutionalAnalysisScheduler,
            sectorAnalysisScheduler, riskAnalysisScheduler, orderBookScheduler, managementCommentaryScheduler,
            newsCatalystScheduler, corporateSignalScheduler, decisionScoringScheduler, decisionSnapshotScheduler,
            forwardOutcomeScheduler, dailyReportScheduler, xbrlEnrichmentScheduler, ownershipTransformationScheduler,
            marketAccumulationScheduler, financialTransformationScheduler, capitalAllocationScheduler,
            sectorContextScheduler, marketInflectionScheduler
        );
    }

    @Test
    void triggerInvokesEachRegisteredJobsOwnMethod() {
        registry.trigger("market-discovery-price-backfill");
        registry.trigger("deal-materiality-scoring");
        registry.trigger("institutional-interpretation");
        registry.trigger("document-processing");
        registry.trigger("technical-analysis");
        registry.trigger("financial-results-bridge");
        registry.trigger("fundamental-analysis");
        registry.trigger("corporate-event-extraction");
        registry.trigger("institutional-analysis");
        registry.trigger("sector-analysis");
        registry.trigger("risk-analysis");
        registry.trigger("order-book");
        registry.trigger("management-commentary");
        registry.trigger("news-catalyst");
        registry.trigger("corporate-signal");
        registry.trigger("decision-scoring");
        registry.trigger("decision-snapshot-archive");
        registry.trigger("forward-outcome-tracking");
        registry.trigger("daily-ai-report");
        registry.trigger("xbrl-shareholding-enrichment");
        registry.trigger("ownership-transformation");
        registry.trigger("market-accumulation-evidence");
        registry.trigger("financial-results-comparision-fetch");
        registry.trigger("capital-allocation-evidence");
        registry.trigger("sector-context-evidence");
        registry.trigger("market-accumulation-evidence-backfill");
        registry.trigger("financial-results-comparision-fetch-backfill");
        registry.trigger("ownership-transformation-backfill");
        registry.trigger("sector-context-evidence-backfill");
        registry.trigger("market-inflection");

        verify(marketPriceBackfillScheduler).runDiscoveryPriceBackfill();
        verify(dealMaterialityScoringScheduler).runDealMaterialityScoring();
        verify(institutionalInterpretationScheduler).runInstitutionalInterpretation();
        verify(documentProcessingScheduler).runDocumentProcessing();
        verify(technicalAnalysisScheduler).runDailyTechnicalAnalysis();
        verify(financialResultsBridgeScheduler).runDailyFinancialResultsBridge();
        verify(fundamentalAnalysisScheduler).runDailyFundamentalAnalysis();
        verify(eventExtractionScheduler).runEventExtraction();
        verify(institutionalAnalysisScheduler).runDailyInstitutionalAnalysis();
        verify(sectorAnalysisScheduler).runDailySectorAnalysis();
        verify(riskAnalysisScheduler).runDailyRiskAnalysis();
        verify(orderBookScheduler).runOrderBookUpdate();
        verify(managementCommentaryScheduler).runManagementCommentaryUpdate();
        verify(newsCatalystScheduler).runNewsCatalystUpdate();
        verify(corporateSignalScheduler).runCorporateSignalUpdate();
        verify(decisionScoringScheduler).runDecisionScoringUpdate();
        verify(decisionSnapshotScheduler).runDecisionSnapshotArchive();
        verify(forwardOutcomeScheduler).runForwardOutcomeTracking();
        verify(dailyReportScheduler).runDailyReportGeneration();
        verify(xbrlEnrichmentScheduler).runXbrlShareholdingEnrichment();
        verify(ownershipTransformationScheduler).runOwnershipTransformation();
        verify(marketAccumulationScheduler).runMarketAccumulationEvidence();
        verify(financialTransformationScheduler).runFinancialResultsComparisionFetch();
        verify(capitalAllocationScheduler).runCapitalAllocationEvidence();
        verify(sectorContextScheduler).runSectorContextEvidence();
        verify(marketAccumulationScheduler).runMarketAccumulationEvidenceBackfill();
        verify(financialTransformationScheduler).runFinancialResultsComparisionFetchBackfill();
        verify(ownershipTransformationScheduler).runOwnershipTransformationBackfill();
        verify(sectorContextScheduler).runSectorContextEvidenceBackfill();
        verify(marketInflectionScheduler).runMarketInflection();
    }
}
