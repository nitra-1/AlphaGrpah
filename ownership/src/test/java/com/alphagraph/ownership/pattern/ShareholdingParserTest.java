package com.alphagraph.ownership.pattern;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShareholdingParserTest {

    private final ShareholdingParser parser = new ShareholdingParser();

    @Test
    void parsesRowsAndTreatsBlankOptionalFieldsAsNull() {
        List<String> lines = List.of(
            "SYMBOL,PERIOD_END,PROMOTER_PCT,FII_PCT,DII_PCT,MF_PCT,PUBLIC_PCT",
            "TCS,2026-06-30,71.77,9.66,13.41,,5.16",
            "RELIANCE,2026-06-30,50.48,17.19,21.04,10.11,11.29"
        );

        List<RawShareholdingRow> parsed = parser.parse(lines);

        assertThat(parsed).hasSize(2);
        RawShareholdingRow tcs = parsed.get(0);
        assertThat(tcs.symbol()).isEqualTo("TCS");
        assertThat(tcs.periodEnd()).isEqualTo("2026-06-30");
        assertThat(tcs.promoterPct()).isEqualTo("71.77");
        assertThat(tcs.mfPct()).isNull();
        assertThat(tcs.publicPct()).isEqualTo("5.16");

        RawShareholdingRow reliance = parsed.get(1);
        assertThat(reliance.mfPct()).isEqualTo("10.11");
    }

    @Test
    void emptyInputProducesNoRows() {
        assertThat(parser.parse(List.of())).isEmpty();
        assertThat(parser.parse(List.of("SYMBOL,PERIOD_END,PROMOTER_PCT,FII_PCT,DII_PCT,MF_PCT,PUBLIC_PCT"))).isEmpty();
    }
}
