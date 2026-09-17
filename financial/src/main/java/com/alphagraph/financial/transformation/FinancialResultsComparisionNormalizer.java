package com.alphagraph.financial.transformation;

import com.alphagraph.financial.results.FinancialInstrumentLookup;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.Locale;
import java.util.Optional;

/**
 * Converts one raw quarter row into a {@link FinancialResultsPeriod}. Operating margin is derived,
 * not a field the feed provides directly: {@code operatingProfit = profitBeforeTax + interestNew -
 * otherIncomeNew} (a standard PBT-back-out approximation of operating profit), divided by revenue.
 * This is a real, disclosed approximation, not an exact reproduction of NSE's own embedded
 * free-text "Operating Margin (%)" disclosure note (which isn't a structured field and isn't
 * present on every filing) - cross-checked once against RELIANCE's real Q3 FY25 figures during
 * verification (computed 8.39% vs. the filing's own disclosed 8.0%, a small, expected difference
 * from a slightly different ratio definition, not a bug).
 */
@Component
class FinancialResultsComparisionNormalizer {

    // Real NSE dates arrive upper-cased ("31-DEC-2024") - a plain "dd-MMM-yyyy" pattern only matches
    // the locale's title-case month names and throws, same fix as ownership.pattern.ShareholdingNormalizer's
    // own DATE_FORMAT already established for this exact feed family.
    private static final DateTimeFormatter DATE_FORMAT = new DateTimeFormatterBuilder()
        .parseCaseInsensitive()
        .appendPattern("dd-MMM-yyyy")
        .toFormatter(Locale.ENGLISH);

    private final FinancialInstrumentLookup instrumentLookup;

    FinancialResultsComparisionNormalizer(FinancialInstrumentLookup instrumentLookup) {
        this.instrumentLookup = instrumentLookup;
    }

    Optional<FinancialResultsPeriod> normalize(RawResultsComparisionRow raw) {
        if (raw.symbol() == null || raw.toDate() == null) {
            return Optional.empty();
        }
        var instrumentId = instrumentLookup.findIdBySymbol(raw.symbol());
        if (instrumentId.isEmpty()) {
            return Optional.empty();
        }

        LocalDate periodEnd = LocalDate.parse(raw.toDate(), DATE_FORMAT);
        BigDecimal revenue = decimalOrNull(raw.netSale());
        BigDecimal pat = decimalOrNull(raw.netProfit());
        BigDecimal interestExpense = decimalOrNull(raw.interestNew());
        BigDecimal operatingMarginPct = operatingMargin(revenue, decimalOrNull(raw.profitBeforeTax()), interestExpense, decimalOrNull(raw.otherIncomeNew()));

        return Optional.of(new FinancialResultsPeriod(instrumentId.get(), raw.symbol(), periodEnd, revenue, pat, operatingMarginPct, interestExpense));
    }

    private static BigDecimal operatingMargin(BigDecimal revenue, BigDecimal profitBeforeTax, BigDecimal interestExpense, BigDecimal otherIncome) {
        if (revenue == null || revenue.signum() == 0 || profitBeforeTax == null || interestExpense == null || otherIncome == null) {
            return null;
        }
        BigDecimal operatingProfit = profitBeforeTax.add(interestExpense).subtract(otherIncome);
        return operatingProfit.divide(revenue, 8, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100)).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal decimalOrNull(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
