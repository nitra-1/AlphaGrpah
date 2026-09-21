package com.alphagraph.ownership.transformation;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * One quarter's full shareholding breakdown, including the four XBRL sub-categories -
 * deliberately not {@code ownership.api.ShareholdingPattern} (which only carries the original 5
 * fields), so this package's own richer needs never ripple into {@code ownership.engine}/
 * {@code intelligence}/{@code api.admin}. Any field may be null - XBRL enrichment fills them in
 * asynchronously, quarter by quarter, so a partially-enriched history is the normal case, not an
 * error.
 *
 * <p>{@code dataAvailableFrom}/{@code xbrlDataAvailableFrom} are real information-availability
 * dates, never the quarter's own {@code periodEnd} - {@link #availableFrom} resolves the correct
 * one for whichever metric actually drove a Stage 2 result (see
 * {@code OwnershipTransformationEngine}'s own javadoc for why this distinction matters).
 */
record TransformationShareholdingPeriod(
    UUID instrumentId, String symbol, LocalDate periodEnd, LocalDate dataAvailableFrom, LocalDate xbrlDataAvailableFrom,
    BigDecimal promoterPercentage, BigDecimal fiiPercentage, BigDecimal diiPercentage,
    BigDecimal mfPercentage, BigDecimal publicPercentage,
    BigDecimal fpiCategory1Percentage, BigDecimal fpiCategory2Percentage,
    BigDecimal insuranceCompaniesPercentage, BigDecimal otherFinancialInstitutionsPercentage
) {

    BigDecimal valueFor(TransformationMetric metric) {
        return switch (metric) {
            case PROMOTER -> promoterPercentage;
            case FII -> fiiPercentage;
            case DII -> diiPercentage;
            case MF -> mfPercentage;
            case PUBLIC -> publicPercentage;
            case FPI_CATEGORY_1 -> fpiCategory1Percentage;
            case FPI_CATEGORY_2 -> fpiCategory2Percentage;
            case INSURANCE_COMPANIES -> insuranceCompaniesPercentage;
            case OTHER_FINANCIAL_INSTITUTIONS -> otherFinancialInstitutionsPercentage;
        };
    }

    /**
     * {@code PROMOTER}/{@code PUBLIC} (and {@code null}, {@code NO_CLEAR_SIGNAL}'s case) resolve to
     * {@code dataAvailableFrom} - the live summary JSON carries these directly. Every other metric
     * only ever becomes real once XBRL enrichment runs, so it resolves to {@code xbrlDataAvailableFrom}
     * - structurally never null when that metric is actually the driving one, since XBRL enrichment
     * fills the metric's value and stamps {@code xbrl_enriched_at} in the same real write.
     */
    LocalDate availableFrom(TransformationMetric metric) {
        if (metric == null || metric == TransformationMetric.PROMOTER || metric == TransformationMetric.PUBLIC) {
            return dataAvailableFrom;
        }
        return xbrlDataAvailableFrom != null ? xbrlDataAvailableFrom : dataAvailableFrom;
    }
}
