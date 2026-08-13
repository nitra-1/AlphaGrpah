package com.alphagraph.market.pricing;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class IndexBhavdataParserTest {

    private final IndexBhavdataParser parser = new IndexBhavdataParser();

    @Test
    void keepsOnlyTheNifty50RowAmongEveryOtherIndex() {
        List<String> lines = List.of(
            "Index Name,Index Date,Open Index Value,High Index Value,Low Index Value,Closing Index Value,Points Change,Change(%),Volume,Turnover (Rs. Cr.),P/E,P/B,Div Yield",
            "Nifty 50,13-08-2026,24431.6,24431.6,24311.4,24395.85,-40.1,-.16,295823169,24255.39,20.59,2.98,1.2",
            "Nifty Next 50,13-08-2026,74824.2,74851,74375.05,74726.65,-21.45,-.03,176828169,11463.66,19.88,3.47,1.1",
            "Nifty Bank,13-08-2026,50000,50100,49800,49950,-50,-0.1,1000000,5000,15,2,1"
        );

        List<RawIndexRow> result = parser.parse(lines);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).indexName()).isEqualTo("Nifty 50");
        assertThat(result.get(0).tradeDate()).isEqualTo("13-08-2026");
        assertThat(result.get(0).open()).isEqualTo("24431.6");
        assertThat(result.get(0).high()).isEqualTo("24431.6");
        assertThat(result.get(0).low()).isEqualTo("24311.4");
        assertThat(result.get(0).close()).isEqualTo("24395.85");
        assertThat(result.get(0).volume()).isEqualTo("295823169");
    }

    @Test
    void returnsNothingWhenNifty50IsAbsent() {
        List<String> lines = List.of(
            "Index Name,Index Date,Open Index Value,High Index Value,Low Index Value,Closing Index Value,Points Change,Change(%),Volume,Turnover (Rs. Cr.),P/E,P/B,Div Yield",
            "Nifty Bank,13-08-2026,50000,50100,49800,49950,-50,-0.1,1000000,5000,15,2,1"
        );

        assertThat(parser.parse(lines)).isEmpty();
    }
}
