package com.alphagraph.ownership.pattern;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class XbrlDocumentParserTest {

    private final XbrlDocumentParser parser = new XbrlDocumentParser();

    // Hand-built fixture using RELIANCE's real confirmed contextRef/value pairs from the live
    // verification (30-Jun-2026 filing) - a minimal but real shape of the actual namespace and
    // element structure, not the full ~500KB document.
    private static final String FIXTURE = """
        <?xml version="1.0" encoding="UTF-8"?>
        <xbrli:xbrl xmlns:xbrli="http://www.xbrl.org/2003/instance"
                     xmlns:in-bse-shp="http://www.bseindia.com/xbrl/shp/2025-10-31/in-bse-shp">
            <in-bse-shp:PercentageOfTotalVotingRights contextRef="ShareholdingOfPromoterAndPromoterGroup_ContextI" decimals="INF" unitRef="pure">0.4962</in-bse-shp:PercentageOfTotalVotingRights>
            <in-bse-shp:PercentageOfTotalVotingRights contextRef="MutualFundsOrUTI_ContextI" decimals="INF" unitRef="pure">0.0993</in-bse-shp:PercentageOfTotalVotingRights>
            <in-bse-shp:PercentageOfTotalVotingRights contextRef="InsuranceCompanies_ContextI" decimals="INF" unitRef="pure">0.0905</in-bse-shp:PercentageOfTotalVotingRights>
            <in-bse-shp:PercentageOfTotalVotingRights contextRef="InstitutionsForeignPortfolioInvestorCategoryOne_ContextI" decimals="INF" unitRef="pure">0.1624</in-bse-shp:PercentageOfTotalVotingRights>
            <in-bse-shp:PercentageOfTotalVotingRights contextRef="InstitutionsForeignPortfolioInvestorCategoryTwo_ContextI" decimals="INF" unitRef="pure">0.0053</in-bse-shp:PercentageOfTotalVotingRights>
            <in-bse-shp:PercentageOfTotalVotingRights contextRef="OtherFinancialInstitutions_ContextI" decimals="INF" unitRef="pure">0</in-bse-shp:PercentageOfTotalVotingRights>
            <in-bse-shp:PercentageOfTotalVotingRights contextRef="NonInstitutions_ContextI" decimals="INF" unitRef="pure">0.1085</in-bse-shp:PercentageOfTotalVotingRights>
        </xbrli:xbrl>
        """;

    @Test
    void parsesRealValuesAndConvertsFractionsToPercentagePoints() {
        Map<XbrlCategory, BigDecimal> result = parser.parse(FIXTURE);

        // 0.4962 -> 49.62, not 0.4962 - the x100 fraction-to-percentage-point conversion is the
        // one thing this test must catch if it regresses.
        assertThat(result.get(XbrlCategory.PROMOTER_CROSSCHECK)).isEqualByComparingTo("49.62");
        assertThat(result.get(XbrlCategory.MF)).isEqualByComparingTo("9.93");
        assertThat(result.get(XbrlCategory.INSURANCE_COMPANIES)).isEqualByComparingTo("9.05");
        assertThat(result.get(XbrlCategory.FPI_CATEGORY_1)).isEqualByComparingTo("16.24");
        assertThat(result.get(XbrlCategory.FPI_CATEGORY_2)).isEqualByComparingTo("0.53");
        assertThat(result.get(XbrlCategory.OTHER_FINANCIAL_INSTITUTIONS)).isEqualByComparingTo("0.00");
    }

    @Test
    void unmappedContextRefIsSkippedNotGuessed() {
        // NonInstitutions_ContextI is real in the fixture but has no XbrlCategory mapping - the
        // fixture has 7 elements, only 6 map to a category, so the result must have exactly 6
        // entries, not 7.
        Map<XbrlCategory, BigDecimal> result = parser.parse(FIXTURE);

        assertThat(result).hasSize(6);
    }

    @Test
    void outOfRangeValueIsDroppedNotPersisted() {
        // Real, live-discovered case: an older filing's taxonomy version produced a value that,
        // once multiplied by 100, is nowhere near a sane percentage - must be dropped rather than
        // risk a numeric field overflow (or a silently wrong persisted value) downstream.
        String withBadValue = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xbrli:xbrl xmlns:xbrli="http://www.xbrl.org/2003/instance"
                         xmlns:in-bse-shp="http://www.bseindia.com/xbrl/shp/2025-10-31/in-bse-shp">
                <in-bse-shp:PercentageOfTotalVotingRights contextRef="MutualFundsOrUTI_ContextI" decimals="INF" unitRef="pure">49.62</in-bse-shp:PercentageOfTotalVotingRights>
                <in-bse-shp:PercentageOfTotalVotingRights contextRef="InsuranceCompanies_ContextI" decimals="INF" unitRef="pure">0.0905</in-bse-shp:PercentageOfTotalVotingRights>
            </xbrli:xbrl>
            """;

        Map<XbrlCategory, BigDecimal> result = parser.parse(withBadValue);

        // 49.62 * 100 = 4962, way outside 0-100 - dropped.
        assertThat(result).doesNotContainKey(XbrlCategory.MF);
        // The other, sane value in the same document is unaffected.
        assertThat(result.get(XbrlCategory.INSURANCE_COMPANIES)).isEqualByComparingTo("9.05");
    }

    @Test
    void emptyDocumentProducesNoEntries() {
        String empty = """
            <?xml version="1.0" encoding="UTF-8"?>
            <xbrli:xbrl xmlns:xbrli="http://www.xbrl.org/2003/instance"
                         xmlns:in-bse-shp="http://www.bseindia.com/xbrl/shp/2025-10-31/in-bse-shp">
            </xbrli:xbrl>
            """;

        assertThat(parser.parse(empty)).isEmpty();
    }
}
