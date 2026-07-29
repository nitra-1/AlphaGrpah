package com.alphagraph.ownership.deals;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class BulkDealsParserTest {

    private final BulkDealsParser parser = new BulkDealsParser();

    @Test
    void parsesTaggedRowsFromBothFeeds() {
        List<String> lines = List.of(
            BulkDealsCollector.COMBINED_HEADER,
            "BULK,28-JUL-2026,AASTHA,Aastha Spintex Limited,D3 STOCK VISION LLP,BUY,222230,83.00,-",
            "BLOCK,28-JUL-2026,RELIANCE,Reliance Industries Ltd,SOME FUND,SELL,600000,1430.50"
        );

        List<RawDealRow> parsed = parser.parse(lines);

        assertThat(parsed).hasSize(2);
        RawDealRow bulk = parsed.get(0);
        assertThat(bulk.dealType()).isEqualTo("BULK");
        assertThat(bulk.symbol()).isEqualTo("AASTHA");
        assertThat(bulk.dealDate()).isEqualTo("28-JUL-2026");
        assertThat(bulk.clientName()).isEqualTo("D3 STOCK VISION LLP");
        assertThat(bulk.buySell()).isEqualTo("BUY");
        assertThat(bulk.quantity()).isEqualTo("222230");
        assertThat(bulk.price()).isEqualTo("83.00");

        RawDealRow block = parsed.get(1);
        assertThat(block.dealType()).isEqualTo("BLOCK");
        assertThat(block.symbol()).isEqualTo("RELIANCE");
        assertThat(block.buySell()).isEqualTo("SELL");
    }

    @Test
    void emptyInputProducesNoRows() {
        assertThat(parser.parse(List.of())).isEmpty();
        assertThat(parser.parse(List.of(BulkDealsCollector.COMBINED_HEADER))).isEmpty();
    }
}
