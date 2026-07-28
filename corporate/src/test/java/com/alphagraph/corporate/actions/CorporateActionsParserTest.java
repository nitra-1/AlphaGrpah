package com.alphagraph.corporate.actions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorporateActionsParserTest {

    private final CorporateActionsParser parser = new CorporateActionsParser();

    @Test
    void parsesRowsAndTreatsBlankOptionalFieldsAsNull() {
        List<String> lines = List.of(
            "SYMBOL,ACTION_TYPE,ANNOUNCEMENT_DATE,EX_DATE,RECORD_DATE,DIVIDEND_AMOUNT,RATIO_NUMERATOR,RATIO_DENOMINATOR,PRICE",
            "TCS,DIVIDEND,2025-04-10,2025-06-04,2025-06-04,30.00,,,",
            "RELIANCE,DIVIDEND,,2025-08-14,2025-08-14,5.50,,,"
        );

        List<RawCorporateActionRow> parsed = parser.parse(lines);

        assertThat(parsed).hasSize(2);
        RawCorporateActionRow tcs = parsed.get(0);
        assertThat(tcs.symbol()).isEqualTo("TCS");
        assertThat(tcs.actionType()).isEqualTo("DIVIDEND");
        assertThat(tcs.announcementDate()).isEqualTo("2025-04-10");
        assertThat(tcs.exDate()).isEqualTo("2025-06-04");
        assertThat(tcs.dividendAmount()).isEqualTo("30.00");
        assertThat(tcs.ratioNumerator()).isNull();
        assertThat(tcs.price()).isNull();

        RawCorporateActionRow reliance = parsed.get(1);
        assertThat(reliance.announcementDate()).isNull();
        assertThat(reliance.dividendAmount()).isEqualTo("5.50");
    }

    @Test
    void emptyInputProducesNoRows() {
        assertThat(parser.parse(List.of())).isEmpty();
        assertThat(parser.parse(List.of(
            "SYMBOL,ACTION_TYPE,ANNOUNCEMENT_DATE,EX_DATE,RECORD_DATE,DIVIDEND_AMOUNT,RATIO_NUMERATOR,RATIO_DENOMINATOR,PRICE"
        ))).isEmpty();
    }
}
