package com.alphagraph.risk.api;

import java.time.LocalDate;
import java.util.UUID;

/**
 * The Risk Engine's input, assembled by {@code intelligence.risk} from four domains: Technical
 * (Module 1.5), Fundamental (Module 1.6), Ownership/Institutional (Module 1.7), plus raw
 * market price and raw financial statement figures for Valuation. All cross-domain signals are
 * plain String/Double here rather than each domain's own enum types (Trend, Momentum,
 * PromoterStatus, ...) - risk must never import another domain module's package tree
 * (docs/001_System_Architecture.md §4, Rule 3), so intelligence.risk converts each domain enum
 * to its {@code name()} before building this record.
 */
public record RiskEngineInput(
    UUID instrumentId,
    String symbol,
    LocalDate asOfDate,

    // Technical (Module 1.5) - drives Technical Risk
    String technicalTrend,
    String technicalMomentum,
    String technicalVolumeState,

    // Fundamental (Module 1.6) - drives Business Risk
    String businessGrowth,
    String profitability,
    String financialQuality,
    Double debtToEquity,
    Double revenueGrowthPercentage,
    Double cashConversionRatio,

    // Institutional/Ownership (Module 1.7) - drives Ownership Risk
    String promoterStatus,
    String fiiStatus,
    String mfStatus,
    String deliveryStatus,

    // Raw market + financial figures - drives Valuation Risk (PE/PB derived, not sourced fresh)
    Double latestClose,
    Double eps,
    Double pat,
    Double totalEquity
) {
}
