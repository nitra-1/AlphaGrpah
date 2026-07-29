package com.alphagraph.financial.results;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialResultsParserTest {

    private final FinancialResultsParser parser = new FinancialResultsParser();

    private static final String HEADER =
        "SYMBOL,PERIOD_END,PERIOD_TYPE,SALES,PAT,EPS,ROE_PCT,ROCE_PCT,OPERATING_MARGIN_PCT,NET_MARGIN_PCT,"
        + "CASH_FLOW_FROM_OPS,TOTAL_ASSETS,CURRENT_ASSETS,CURRENT_LIABILITIES,TOTAL_DEBT,TOTAL_EQUITY,"
        + "INTEREST_EXPENSE,EBIT";

    @Test
    void parsesRowsAndTreatsBlankOptionalFieldsAsNull() {
        List<String> lines = List.of(
            HEADER,
            "TCS,2025-03-31,QUARTERLY,64479.00,12224.00,33.79,51.50,69.80,24.20,19.00,,159629.00,123000.00,53001.00,0.00,94756.00,,15601.00",
            "HDFCBANK,2025-03-31,QUARTERLY,32065.80,17616.00,,14.40,,,,,,,,,,,"
        );

        List<RawFinancialResultRow> parsed = parser.parse(lines);

        assertThat(parsed).hasSize(2);
        RawFinancialResultRow tcs = parsed.get(0);
        assertThat(tcs.symbol()).isEqualTo("TCS");
        assertThat(tcs.periodEnd()).isEqualTo("2025-03-31");
        assertThat(tcs.periodType()).isEqualTo("QUARTERLY");
        assertThat(tcs.sales()).isEqualTo("64479.00");
        assertThat(tcs.eps()).isEqualTo("33.79");
        assertThat(tcs.cashFlowFromOps()).isNull();
        assertThat(tcs.totalAssets()).isEqualTo("159629.00");
        assertThat(tcs.totalDebt()).isEqualTo("0.00");
        assertThat(tcs.ebit()).isEqualTo("15601.00");
        assertThat(tcs.interestExpense()).isNull();

        RawFinancialResultRow hdfcbank = parsed.get(1);
        assertThat(hdfcbank.eps()).isNull();
        assertThat(hdfcbank.roePct()).isEqualTo("14.40");
        assertThat(hdfcbank.rocePct()).isNull();
        assertThat(hdfcbank.totalAssets()).isNull();
    }

    @Test
    void emptyInputProducesNoRows() {
        assertThat(parser.parse(List.of())).isEmpty();
        assertThat(parser.parse(List.of(HEADER))).isEmpty();
    }
}
