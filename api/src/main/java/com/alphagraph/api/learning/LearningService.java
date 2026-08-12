package com.alphagraph.api.learning;

import com.alphagraph.learning.performance.EvidenceSummary;
import com.alphagraph.learning.performance.ModelPerformanceReader;
import com.alphagraph.learning.performance.RatingHitRate;
import org.springframework.stereotype.Service;

/** Assembles the Learning Dashboard's DTOs from learning.performance.ModelPerformanceReader, per docs/004_API_Architecture.md §5 - same pattern api.dashboard.DashboardService already uses for corporate's readers. */
@Service
public class LearningService {

    private final ModelPerformanceReader modelPerformanceReader;

    public LearningService(ModelPerformanceReader modelPerformanceReader) {
        this.modelPerformanceReader = modelPerformanceReader;
    }

    public LearningEvidenceSummaryDto evidenceSummary() {
        EvidenceSummary summary = modelPerformanceReader.evidenceSummary();
        return new LearningEvidenceSummaryDto(
            summary.daysOfHistory(), summary.decisionSnapshotCount(), summary.forwardOutcomesComputed(),
            modelPerformanceReader.swingRatingHitRates().stream().map(LearningService::toDto).toList(),
            modelPerformanceReader.longTermRatingHitRates().stream().map(LearningService::toDto).toList()
        );
    }

    private static RatingHitRateDto toDto(RatingHitRate rate) {
        return new RatingHitRateDto(rate.rating().name(), rate.horizonDays(), rate.sampleSize(), rate.hitRatePercentage(), rate.sufficientData());
    }
}
