package com.alphagraph.corporate.documents;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnnouncementsParserTest {

    private final AnnouncementsParser parser = new AnnouncementsParser();

    @Test
    void parsesRealNseAnnouncementFields() {
        String json = """
            [
              {
                "symbol": "RELIANCE",
                "sm_isin": "INE002A01018",
                "sm_name": "Reliance Industries Limited",
                "desc": "Updates",
                "attchmntText": "Presentation on the resolutions proposed for shareholders approval",
                "attchmntFile": "https://nsearchives.nseindia.com/corporate/kavinavora_25072026143701_RIL_SELetter_250726.pdf",
                "an_dt": "25-Jul-2026 14:37:56",
                "seq_id": "106710951",
                "smIndustry": "Refineries",
                "hasXbrl": true
              }
            ]
            """;

        List<RawAnnouncementRow> parsed = parser.parse(json);

        assertThat(parsed).hasSize(1);
        RawAnnouncementRow row = parsed.get(0);
        assertThat(row.symbol()).isEqualTo("RELIANCE");
        assertThat(row.isin()).isEqualTo("INE002A01018");
        assertThat(row.category()).isEqualTo("Updates");
        assertThat(row.title()).isEqualTo("Presentation on the resolutions proposed for shareholders approval");
        assertThat(row.pdfUrl()).isEqualTo("https://nsearchives.nseindia.com/corporate/kavinavora_25072026143701_RIL_SELetter_250726.pdf");
        assertThat(row.announcedAt()).isEqualTo("25-Jul-2026 14:37:56");
        assertThat(row.externalId()).isEqualTo("106710951");
    }

    @Test
    void missingOptionalFieldsBecomeNullRatherThanThrowing() {
        String json = """
            [{"symbol": "TCS", "seq_id": "1", "an_dt": "27-Jul-2026 15:05:32", "attchmntFile": "https://x/y.pdf"}]
            """;

        List<RawAnnouncementRow> parsed = parser.parse(json);

        assertThat(parsed).hasSize(1);
        assertThat(parsed.get(0).isin()).isNull();
        assertThat(parsed.get(0).category()).isNull();
        assertThat(parsed.get(0).title()).isNull();
    }

    @Test
    void emptyArrayProducesNoRows() {
        assertThat(parser.parse("[]")).isEmpty();
    }
}
