package com.alphagraph.market.pricing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BhavdataParserTest {

    private final BhavdataParser parser = new BhavdataParser();

    @Test
    void parsesEqRowsAndSkipsHeaderAndNonEqSeries() {
        List<String> lines = List.of(
            "SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER",
            "ACE, EQ, 24-Jul-2026, 979.00, 975.20, 1051.00, 971.60, 1040.90, 1039.40, 1027.67, 2105588, 21638.42, 80353, 475167, 22.57",
            "1018GS2026, GS, 24-Jul-2026, 104.29, 104.00, 104.25, 104.00, 104.25, 104.25, 104.02, 666, 0.69, 6, 666, 100.00"
        );

        List<RawDeliveryRow> parsed = parser.parse(lines);

        assertThat(parsed).hasSize(1);
        RawDeliveryRow row = parsed.get(0);
        assertThat(row.symbol()).isEqualTo("ACE");
        assertThat(row.series()).isEqualTo("EQ");
        assertThat(row.tradeDate()).isEqualTo("24-Jul-2026");
        assertThat(row.open()).isEqualTo("975.20");
        assertThat(row.high()).isEqualTo("1051.00");
        assertThat(row.low()).isEqualTo("971.60");
        assertThat(row.close()).isEqualTo("1039.40");
        assertThat(row.volume()).isEqualTo("2105588");
        assertThat(row.deliveryPercentage()).isEqualTo("22.57");
    }

    @Test
    void emptyInputProducesNoRows() {
        assertThat(parser.parse(List.of())).isEmpty();
        assertThat(parser.parse(List.of("SYMBOL, SERIES, DATE1"))).isEmpty();
    }
}
