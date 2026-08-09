package com.alphagraph.reference.securitymaster;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityMasterParserTest {

    private final SecurityMasterParser parser = new SecurityMasterParser();

    @Test
    void parsesEqRowsAndSkipsHeaderAndNonEqSeries() {
        List<String> lines = List.of(
            "SYMBOL,NAME OF COMPANY, SERIES, DATE OF LISTING, PAID UP VALUE, MARKET LOT, ISIN NUMBER, FACE VALUE",
            "SBIN,State Bank of India,EQ,01-MAR-1995,1,1,INE062A01020,1",
            "1018GS2026,Government of India,GS,10-JAN-2018,100,1,IN0020180018,100"
        );

        List<RawSecurityMasterRow> parsed = parser.parse(lines);

        assertThat(parsed).hasSize(1);
        RawSecurityMasterRow row = parsed.get(0);
        assertThat(row.symbol()).isEqualTo("SBIN");
        assertThat(row.companyName()).isEqualTo("State Bank of India");
        assertThat(row.series()).isEqualTo("EQ");
        assertThat(row.listingDate()).isEqualTo("01-MAR-1995");
        assertThat(row.isin()).isEqualTo("INE062A01020");
        assertThat(row.faceValue()).isEqualTo("1");
    }

    @Test
    void emptyInputProducesNoRows() {
        assertThat(parser.parse(List.of())).isEmpty();
        assertThat(parser.parse(List.of("SYMBOL,NAME OF COMPANY, SERIES"))).isEmpty();
    }
}
