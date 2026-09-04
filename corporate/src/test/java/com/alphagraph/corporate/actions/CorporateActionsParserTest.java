package com.alphagraph.corporate.actions;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CorporateActionsParserTest {

    private final CorporateActionsParser parser = new CorporateActionsParser();

    @Test
    void parsesRealShapedRowsAndNormalizesDashPlaceholdersToNull() {
        String json = """
            [
              {"symbol": "TCS", "subject": "Dividend - Rs 30.00 Per Share", "exDate": "04-Jun-2025",
               "recDate": "04-Jun-2025", "caBroadcastDate": "10-Apr-2025"},
              {"symbol": "GOODLUCK", "subject": "Bonus 2:1", "exDate": "21-Aug-2026",
               "recDate": "-", "caBroadcastDate": null}
            ]
            """;

        List<RawCorporateActionRow> parsed = parser.parse(json);

        assertThat(parsed).hasSize(2);
        RawCorporateActionRow tcs = parsed.get(0);
        assertThat(tcs.symbol()).isEqualTo("TCS");
        assertThat(tcs.subject()).isEqualTo("Dividend - Rs 30.00 Per Share");
        assertThat(tcs.exDate()).isEqualTo("04-Jun-2025");
        assertThat(tcs.recordDate()).isEqualTo("04-Jun-2025");
        assertThat(tcs.announcementDate()).isEqualTo("10-Apr-2025");

        RawCorporateActionRow goodluck = parsed.get(1);
        assertThat(goodluck.subject()).isEqualTo("Bonus 2:1");
        assertThat(goodluck.recordDate()).isNull(); // "-" placeholder normalized to null
        assertThat(goodluck.announcementDate()).isNull(); // real JSON null
    }

    @Test
    void emptyArrayProducesNoRows() {
        assertThat(parser.parse("[]")).isEmpty();
    }
}
