package com.alphagraph.ownership.pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

/**
 * Persists one shareholding quarter's XBRL-derived institutional breakdown. {@code fii_percentage}
 * (= FPI Category 1 + Category 2) and {@code dii_percentage}/{@code mf_percentage} (MF is already a
 * sub-category of DII per {@code ownership.api.ShareholdingPattern}'s own existing javadoc, so
 * {@code dii = MF + Insurance + OtherFinancialInstitutions}) are only computed and written when
 * every required input for that derivation is present - a partially-extracted filing leaves the
 * derived field null rather than computing a wrong partial sum.
 *
 * <p>Deliberately does NOT overwrite {@code promoter_percentage} with the XBRL filing's own
 * promoter figure - it's already sourced from the daily summary JSON, and the two real sources
 * showed a small real methodology difference during verification (49.62 vs 50.48 for the same
 * RELIANCE quarter). Only logs a discrepancy when it exceeds a small tolerance, as a data-quality
 * signal, never silently swapping the persisted value's source mid-history.
 *
 * <p>Always stamps {@code xbrl_enriched_at}, regardless of how many of the four categories the
 * document actually contained - real bug found live: a candidate query keyed off "any category
 * still null" re-fetched the same real document forever for any filing that genuinely lacks one
 * of the four categories (e.g. a company with no foreign portfolio investors at all). This column
 * separates "we attempted this period" from "what we extracted" - see V14's migration comment.
 */
@Component
class XbrlShareholdingWriter {

    private static final Logger log = LoggerFactory.getLogger(XbrlShareholdingWriter.class);
    private static final BigDecimal PROMOTER_DISCREPANCY_TOLERANCE = BigDecimal.valueOf(2);

    private final JdbcTemplate jdbcTemplate;

    XbrlShareholdingWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    void write(UUID instrumentId, LocalDate periodEnd, Map<XbrlCategory, BigDecimal> categories, BigDecimal storedPromoterPercentage, String symbol) {
        BigDecimal fpi1 = categories.get(XbrlCategory.FPI_CATEGORY_1);
        BigDecimal fpi2 = categories.get(XbrlCategory.FPI_CATEGORY_2);
        BigDecimal insurance = categories.get(XbrlCategory.INSURANCE_COMPANIES);
        BigDecimal otherFi = categories.get(XbrlCategory.OTHER_FINANCIAL_INSTITUTIONS);
        BigDecimal mf = categories.get(XbrlCategory.MF);

        BigDecimal fii = (fpi1 != null && fpi2 != null) ? fpi1.add(fpi2) : null;
        BigDecimal dii = (mf != null && insurance != null && otherFi != null) ? mf.add(insurance).add(otherFi) : null;

        logPromoterDiscrepancyIfAny(symbol, periodEnd, storedPromoterPercentage, categories.get(XbrlCategory.PROMOTER_CROSSCHECK));

        jdbcTemplate.update(
            """
            UPDATE ownership.shareholding_pattern
            SET fpi_category_1_percentage = COALESCE(?, fpi_category_1_percentage),
                fpi_category_2_percentage = COALESCE(?, fpi_category_2_percentage),
                insurance_companies_percentage = COALESCE(?, insurance_companies_percentage),
                other_financial_institutions_percentage = COALESCE(?, other_financial_institutions_percentage),
                fii_percentage = COALESCE(?, fii_percentage),
                dii_percentage = COALESCE(?, dii_percentage),
                mf_percentage = COALESCE(?, mf_percentage),
                xbrl_enriched_at = now()
            WHERE instrument_id = ? AND period_end = ?
            """,
            fpi1, fpi2, insurance, otherFi, fii, dii, mf, instrumentId, periodEnd
        );
    }

    private void logPromoterDiscrepancyIfAny(String symbol, LocalDate periodEnd, BigDecimal stored, BigDecimal fromXbrl) {
        if (stored == null || fromXbrl == null) {
            return;
        }
        BigDecimal diff = stored.subtract(fromXbrl).abs();
        if (diff.compareTo(PROMOTER_DISCREPANCY_TOLERANCE) > 0) {
            log.info(
                "Promoter percentage discrepancy for {} {}: summary JSON={}, XBRL filing={} (diff={}pp) - keeping the summary JSON value",
                symbol, periodEnd, stored, fromXbrl, diff
            );
        }
    }
}
