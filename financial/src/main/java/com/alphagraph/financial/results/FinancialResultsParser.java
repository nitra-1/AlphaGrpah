package com.alphagraph.financial.results;

import com.alphagraph.common.etl.Parser;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Column order: SYMBOL, PERIOD_END, PERIOD_TYPE, SALES, PAT, EPS, ROE_PCT, ROCE_PCT,
 * OPERATING_MARGIN_PCT, NET_MARGIN_PCT, CASH_FLOW_FROM_OPS, TOTAL_ASSETS, CURRENT_ASSETS,
 * CURRENT_LIABILITIES, TOTAL_DEBT, TOTAL_EQUITY, INTEREST_EXPENSE, EBIT.
 */
@Component
public class FinancialResultsParser implements Parser<List<String>, RawFinancialResultRow> {

    @Override
    public List<RawFinancialResultRow> parse(List<String> lines) {
        return lines.stream()
            .skip(1)
            .filter(line -> !line.isBlank())
            .map(line -> line.split(",", -1))
            .filter(fields -> fields.length >= 18)
            .map(fields -> new RawFinancialResultRow(
                fields[0].trim(), fields[1].trim(), fields[2].trim(), fields[3].trim(), fields[4].trim(),
                blankToNull(fields[5]), blankToNull(fields[6]), blankToNull(fields[7]),
                blankToNull(fields[8]), blankToNull(fields[9]), blankToNull(fields[10]),
                blankToNull(fields[11]), blankToNull(fields[12]), blankToNull(fields[13]),
                blankToNull(fields[14]), blankToNull(fields[15]), blankToNull(fields[16]), blankToNull(fields[17])
            ))
            .toList();
    }

    private static String blankToNull(String value) {
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
