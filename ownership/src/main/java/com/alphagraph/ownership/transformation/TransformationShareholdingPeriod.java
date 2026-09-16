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
 */
record TransformationShareholdingPeriod(
    UUID instrumentId, String symbol, LocalDate periodEnd,
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
}
