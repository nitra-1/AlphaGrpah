package com.alphagraph.ownership.pattern;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ShareholdingParserTest {

    private final ShareholdingParser parser = new ShareholdingParser();

    @Test
    void parsesRealShapedRowsAndLeavesFiiDiiMfNull() {
        String json = """
            [
              {"symbol": "TCS", "date": "30-JUN-2026", "pr_and_prgrp": "71.77", "public_val": "5.16",
               "xbrl": "https://nsearchives.nseindia.com/corporate/xbrl/SHP_1.xml"},
              {"symbol": "ZOMATO", "date": "30-JUN-2026", "pr_and_prgrp": "0.00", "public_val": "40.00", "xbrl": null}
            ]
            """;

        List<RawShareholdingRow> parsed = parser.parse(json);

        assertThat(parsed).hasSize(2);
        RawShareholdingRow tcs = parsed.get(0);
        assertThat(tcs.symbol()).isEqualTo("TCS");
        assertThat(tcs.periodEnd()).isEqualTo("30-JUN-2026");
        assertThat(tcs.promoterPct()).isEqualTo("71.77");
        assertThat(tcs.publicPct()).isEqualTo("5.16");
        assertThat(tcs.xbrlUrl()).isEqualTo("https://nsearchives.nseindia.com/corporate/xbrl/SHP_1.xml");
        // The live summary feed doesn't carry these at all - always null until XBRL enrichment.
        assertThat(tcs.fiiPct()).isNull();
        assertThat(tcs.diiPct()).isNull();
        assertThat(tcs.mfPct()).isNull();

        RawShareholdingRow zomato = parsed.get(1);
        assertThat(zomato.xbrlUrl()).isNull();
    }

    @Test
    void emptyArrayProducesNoRows() {
        assertThat(parser.parse("[]")).isEmpty();
    }
}
