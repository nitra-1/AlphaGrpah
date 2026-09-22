package com.alphagraph.api.admin;

import com.alphagraph.corporate.commentary.ManagementCommentaryScheduler;
import com.alphagraph.corporate.events.EventExtractionScheduler;
import com.alphagraph.corporate.knowledge.KnowledgeExtractionScheduler;
import com.alphagraph.corporate.news.NewsCatalystScheduler;
import com.alphagraph.corporate.orderbook.OrderBookScheduler;
import com.alphagraph.corporate.processing.DocumentProcessingScheduler;
import com.alphagraph.corporate.signal.CorporateSignalScheduler;
import com.alphagraph.corporate.transformation.CapitalAllocationInflectionScheduler;
import com.alphagraph.corporate.transformation.CapitalAllocationScheduler;
import com.alphagraph.decision.engine.DecisionScoringScheduler;
import com.alphagraph.decision.report.DailyReportScheduler;
import com.alphagraph.financial.engine.FundamentalAnalysisScheduler;
import com.alphagraph.financial.transformation.FinancialInflectionScheduler;
import com.alphagraph.financial.transformation.FinancialTransformationScheduler;
import com.alphagraph.intelligence.financial.FinancialResultsBridgeScheduler;
import com.alphagraph.intelligence.institutional.InstitutionalAnalysisScheduler;
import com.alphagraph.intelligence.risk.RiskAnalysisScheduler;
import com.alphagraph.intelligence.riskcontradiction.RiskContradictionScheduler;
import com.alphagraph.intelligence.sector.SectorAnalysisScheduler;
import com.alphagraph.intelligence.sectorcontext.SectorContextScheduler;
import com.alphagraph.intelligence.technical.TechnicalAnalysisScheduler;
import com.alphagraph.learning.outcomes.ForwardOutcomeScheduler;
import com.alphagraph.learning.snapshot.DecisionSnapshotScheduler;
import com.alphagraph.market.pricing.MarketPriceBackfillScheduler;
import com.alphagraph.market.transformation.MarketAccumulationScheduler;
import com.alphagraph.market.transformation.MarketInflectionScheduler;
import com.alphagraph.market.transformation.MarketTransformationSequenceScheduler;
import com.alphagraph.ownership.deals.DealMaterialityScoringScheduler;
import com.alphagraph.ownership.interpretation.InstitutionalInterpretationScheduler;
import com.alphagraph.ownership.pattern.XbrlEnrichmentScheduler;
import com.alphagraph.ownership.transformation.OwnershipTransformationScheduler;
import com.alphagraph.ownership.transformation.OwnershipTransformationSequenceScheduler;
import com.alphagraph.sector.transformation.SectorInflectionScheduler;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Manual re-trigger dispatch for 39 jobs - 33 standalone {@code @Scheduled} jobs plus 6 one-off
 * historical backfills (market/financial/ownership/sector-context Stage 1 evidence, plus Market's
 * and Ownership's own Stage 3 sequence backfills) that have no {@code @Scheduled} annotation at all
 * and only ever run via this registry's {@link #trigger} - the {@code
 * api.admin} analog of {@code scheduler.PipelineRegistry}, which already supports this for the 9
 * ETL pipelines via {@code PipelineDefinitionController}. Every entry wraps the exact same
 * Scheduler bean method Spring's own cron trigger would call (or, for the backfills, the only
 * way that method is ever called), so a manual retry gets identical {@code JobRunTracker}
 * bookkeeping (a real RUNNING -> SUCCESS/FAILED row in {@code scheduler.job_runs}) as a real cron
 * firing - no separate retry-tracking mechanism invented, and this can never drift from what each
 * scheduler actually does since it calls the scheduler directly rather than reimplementing its
 * body. The backfills are deliberately absent from {@code CronMonitoringRepository.JOB_SCHEDULES} -
 * they're one-off catch-up operations, not recurring crons, and showing a fake "next scheduled
 * run" time for one on the admin dashboard would be misleading.
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
        DailyReportScheduler dailyReportScheduler,
        XbrlEnrichmentScheduler xbrlEnrichmentScheduler,
        OwnershipTransformationScheduler ownershipTransformationScheduler,
        MarketAccumulationScheduler marketAccumulationScheduler,
        FinancialTransformationScheduler financialTransformationScheduler,
        CapitalAllocationScheduler capitalAllocationScheduler,
        SectorContextScheduler sectorContextScheduler,
        MarketInflectionScheduler marketInflectionScheduler,
        FinancialInflectionScheduler financialInflectionScheduler,
        CapitalAllocationInflectionScheduler capitalAllocationInflectionScheduler,
        SectorInflectionScheduler sectorInflectionScheduler,
        RiskContradictionScheduler riskContradictionScheduler,
        MarketTransformationSequenceScheduler marketTransformationSequenceScheduler,
        OwnershipTransformationSequenceScheduler ownershipTransformationSequenceScheduler
    ) {
        jobs.put("market-discovery-price-backfill", marketPriceBackfillScheduler::runDiscoveryPriceBackfill);
        jobs.put("deal-materiality-scoring", dealMaterialityScoringScheduler::runDealMaterialityScoring);
        jobs.put("institutional-interpretation", institutionalInterpretationScheduler::runInstitutionalInterpretation);
        jobs.put("xbrl-shareholding-enrichment", xbrlEnrichmentScheduler::runXbrlShareholdingEnrichment);
        jobs.put("ownership-transformation", ownershipTransformationScheduler::runOwnershipTransformation);
        jobs.put("market-accumulation-evidence", marketAccumulationScheduler::runMarketAccumulationEvidence);
        jobs.put("financial-results-comparision-fetch", financialTransformationScheduler::runFinancialResultsComparisionFetch);
        jobs.put("capital-allocation-evidence", capitalAllocationScheduler::runCapitalAllocationEvidence);
        jobs.put("sector-context-evidence", sectorContextScheduler::runSectorContextEvidence);
        jobs.put("market-accumulation-evidence-backfill", marketAccumulationScheduler::runMarketAccumulationEvidenceBackfill);
        jobs.put("financial-results-comparision-fetch-backfill", financialTransformationScheduler::runFinancialResultsComparisionFetchBackfill);
        jobs.put("ownership-transformation-backfill", ownershipTransformationScheduler::runOwnershipTransformationBackfill);
        jobs.put("sector-context-evidence-backfill", sectorContextScheduler::runSectorContextEvidenceBackfill);
        jobs.put("market-inflection", marketInflectionScheduler::runMarketInflection);
        jobs.put("financial-inflection", financialInflectionScheduler::runFinancialInflection);
        jobs.put("capital-allocation-inflection", capitalAllocationInflectionScheduler::runCapitalAllocationInflection);
        jobs.put("sector-inflection", sectorInflectionScheduler::runSectorInflection);
        jobs.put("risk-contradiction-inflection", riskContradictionScheduler::runRiskContradiction);
        jobs.put("market-transformation-sequences", marketTransformationSequenceScheduler::runMarketTransformationSequences);
        jobs.put("market-transformation-sequences-backfill", marketTransformationSequenceScheduler::runMarketTransformationSequencesBackfill);
        jobs.put("ownership-transformation-sequences", ownershipTransformationSequenceScheduler::runOwnershipTransformationSequences);
        jobs.put("ownership-transformation-sequences-backfill", ownershipTransformationSequenceScheduler::runOwnershipTransformationSequencesBackfill);
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
