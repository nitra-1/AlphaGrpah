package com.alphagraph.api.admin;

import com.alphagraph.corporate.commentary.ManagementCommentaryScheduler;
import com.alphagraph.corporate.events.EventExtractionScheduler;
import com.alphagraph.corporate.knowledge.KnowledgeExtractionScheduler;
import com.alphagraph.corporate.news.NewsCatalystScheduler;
import com.alphagraph.corporate.orderbook.OrderBookScheduler;
import com.alphagraph.corporate.processing.DocumentProcessingScheduler;
import com.alphagraph.corporate.signal.CorporateSignalScheduler;
import com.alphagraph.decision.engine.DecisionScoringScheduler;
import com.alphagraph.decision.report.DailyReportScheduler;
import com.alphagraph.financial.engine.FundamentalAnalysisScheduler;
import com.alphagraph.intelligence.financial.FinancialResultsBridgeScheduler;
import com.alphagraph.intelligence.institutional.InstitutionalAnalysisScheduler;
import com.alphagraph.intelligence.risk.RiskAnalysisScheduler;
import com.alphagraph.intelligence.sector.SectorAnalysisScheduler;
import com.alphagraph.intelligence.technical.TechnicalAnalysisScheduler;
import com.alphagraph.learning.outcomes.ForwardOutcomeScheduler;
import com.alphagraph.learning.snapshot.DecisionSnapshotScheduler;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class JobRegistryTest {

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

    private final JobRegistry registry = new JobRegistry(
        documentProcessingScheduler, knowledgeExtractionScheduler, technicalAnalysisScheduler,
        financialResultsBridgeScheduler, fundamentalAnalysisScheduler, eventExtractionScheduler,
        institutionalAnalysisScheduler, sectorAnalysisScheduler, riskAnalysisScheduler, orderBookScheduler,
        managementCommentaryScheduler, newsCatalystScheduler, corporateSignalScheduler, decisionScoringScheduler,
        decisionSnapshotScheduler, forwardOutcomeScheduler, dailyReportScheduler
    );

    private static final List<String> ALL_17_JOB_NAMES = List.of(
        "document-processing", "knowledge-extraction", "technical-analysis", "financial-results-bridge",
        "fundamental-analysis", "corporate-event-extraction", "institutional-analysis", "sector-analysis",
        "risk-analysis", "order-book", "management-commentary", "news-catalyst", "corporate-signal",
        "decision-scoring", "decision-snapshot-archive", "forward-outcome-tracking", "daily-ai-report"
    );

    @Test
    void containsExactlyAllSeventeenRealJobNames() {
        for (String jobName : ALL_17_JOB_NAMES) {
            assertThat(registry.contains(jobName)).as("contains(%s)", jobName).isTrue();
        }
        assertThat(registry.contains("not-a-real-job")).isFalse();
    }

    @Test
    void triggerInvokesOnlyTheMatchingSchedulerMethod() {
        registry.trigger("knowledge-extraction");

        verify(knowledgeExtractionScheduler).runKnowledgeExtraction();
        verifyNoInteractions(
            documentProcessingScheduler, technicalAnalysisScheduler, financialResultsBridgeScheduler,
            fundamentalAnalysisScheduler, eventExtractionScheduler, institutionalAnalysisScheduler,
            sectorAnalysisScheduler, riskAnalysisScheduler, orderBookScheduler, managementCommentaryScheduler,
            newsCatalystScheduler, corporateSignalScheduler, decisionScoringScheduler, decisionSnapshotScheduler,
            forwardOutcomeScheduler, dailyReportScheduler
        );
    }

    @Test
    void triggerInvokesEachRegisteredJobsOwnMethod() {
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
    }
}
