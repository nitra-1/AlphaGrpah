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
import com.alphagraph.market.pricing.MarketPriceBackfillScheduler;
import com.alphagraph.ownership.deals.DealMaterialityScoringScheduler;
import com.alphagraph.ownership.interpretation.InstitutionalInterpretationScheduler;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual re-trigger dispatch for the 20 standalone {@code @Scheduled} jobs - the {@code
 * api.admin} analog of {@code scheduler.PipelineRegistry}, which already supports this for the 9
 * ETL pipelines via {@code PipelineDefinitionController}. Every entry wraps the exact same
 * Scheduler bean method Spring's own cron trigger would call, so a manual retry gets identical
 * {@code JobRunTracker} bookkeeping (a real RUNNING -> SUCCESS/FAILED row in
 * {@code scheduler.job_runs}) as a real cron firing - no separate retry-tracking mechanism
 * invented, and this can never drift from what each scheduler actually does since it calls the
 * scheduler directly rather than reimplementing its body.
 */
@Component
class JobRegistry {

    private final Map<String, Runnable> jobs = new LinkedHashMap<>();

    JobRegistry(
        MarketPriceBackfillScheduler marketPriceBackfillScheduler,
        DealMaterialityScoringScheduler dealMaterialityScoringScheduler,
        InstitutionalInterpretationScheduler institutionalInterpretationScheduler,
        DocumentProcessingScheduler documentProcessingScheduler,
        KnowledgeExtractionScheduler knowledgeExtractionScheduler,
        TechnicalAnalysisScheduler technicalAnalysisScheduler,
        FinancialResultsBridgeScheduler financialResultsBridgeScheduler,
        FundamentalAnalysisScheduler fundamentalAnalysisScheduler,
        EventExtractionScheduler eventExtractionScheduler,
        InstitutionalAnalysisScheduler institutionalAnalysisScheduler,
        SectorAnalysisScheduler sectorAnalysisScheduler,
        RiskAnalysisScheduler riskAnalysisScheduler,
        OrderBookScheduler orderBookScheduler,
        ManagementCommentaryScheduler managementCommentaryScheduler,
        NewsCatalystScheduler newsCatalystScheduler,
        CorporateSignalScheduler corporateSignalScheduler,
        DecisionScoringScheduler decisionScoringScheduler,
        DecisionSnapshotScheduler decisionSnapshotScheduler,
        ForwardOutcomeScheduler forwardOutcomeScheduler,
        DailyReportScheduler dailyReportScheduler
    ) {
        jobs.put("market-discovery-price-backfill", marketPriceBackfillScheduler::runDiscoveryPriceBackfill);
        jobs.put("deal-materiality-scoring", dealMaterialityScoringScheduler::runDealMaterialityScoring);
        jobs.put("institutional-interpretation", institutionalInterpretationScheduler::runInstitutionalInterpretation);
        jobs.put("document-processing", documentProcessingScheduler::runDocumentProcessing);
        jobs.put("knowledge-extraction", knowledgeExtractionScheduler::runKnowledgeExtraction);
        jobs.put("technical-analysis", technicalAnalysisScheduler::runDailyTechnicalAnalysis);
        jobs.put("financial-results-bridge", financialResultsBridgeScheduler::runDailyFinancialResultsBridge);
        jobs.put("fundamental-analysis", fundamentalAnalysisScheduler::runDailyFundamentalAnalysis);
        jobs.put("corporate-event-extraction", eventExtractionScheduler::runEventExtraction);
        jobs.put("institutional-analysis", institutionalAnalysisScheduler::runDailyInstitutionalAnalysis);
        jobs.put("sector-analysis", sectorAnalysisScheduler::runDailySectorAnalysis);
        jobs.put("risk-analysis", riskAnalysisScheduler::runDailyRiskAnalysis);
        jobs.put("order-book", orderBookScheduler::runOrderBookUpdate);
        jobs.put("management-commentary", managementCommentaryScheduler::runManagementCommentaryUpdate);
        jobs.put("news-catalyst", newsCatalystScheduler::runNewsCatalystUpdate);
        jobs.put("corporate-signal", corporateSignalScheduler::runCorporateSignalUpdate);
        jobs.put("decision-scoring", decisionScoringScheduler::runDecisionScoringUpdate);
        jobs.put("decision-snapshot-archive", decisionSnapshotScheduler::runDecisionSnapshotArchive);
        jobs.put("forward-outcome-tracking", forwardOutcomeScheduler::runForwardOutcomeTracking);
        jobs.put("daily-ai-report", dailyReportScheduler::runDailyReportGeneration);
    }

    boolean contains(String jobName) {
        return jobs.containsKey(jobName);
    }

    /** Runs the named job synchronously, on the calling thread - same as {@code PipelineDefinitionController}'s pipeline retrigger. */
    void trigger(String jobName) {
        jobs.get(jobName).run();
    }
}
