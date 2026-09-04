package com.alphagraph.corporate.actions;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class CorporateActionSubjectParserTest {

    @Test
    void dividendSubjectExtractsAmount() {
        Optional<CorporateActionSubjectParser.ParsedSubject> result =
            CorporateActionSubjectParser.parse("Dividend - Rs 1.50 Per Share");

        assertThat(result).isPresent();
        assertThat(result.get().actionType()).isEqualTo("DIVIDEND");
        assertThat(result.get().dividendAmount()).isEqualByComparingTo("1.50");
        assertThat(result.get().ratioNumerator()).isNull();
        assertThat(result.get().price()).isNull();
    }

    @Test
    void bonusSubjectExtractsRatio() {
        Optional<CorporateActionSubjectParser.ParsedSubject> result = CorporateActionSubjectParser.parse("Bonus 2:1");

        assertThat(result).isPresent();
        assertThat(result.get().actionType()).isEqualTo("BONUS");
        assertThat(result.get().ratioNumerator()).isEqualTo(2);
        assertThat(result.get().ratioDenominator()).isEqualTo(1);
    }

    @Test
    void bonusViaSchemeOfArrangementStillClassifiesAsBonus() {
        // Real production subject - a bonus executed via a scheme of arrangement mechanism is
        // still fundamentally a bonus action.
        Optional<CorporateActionSubjectParser.ParsedSubject> result =
            CorporateActionSubjectParser.parse("Scheme Of Arrangement - Bonus Ncrps 4:1");

        assertThat(result).isPresent();
        assertThat(result.get().actionType()).isEqualTo("BONUS");
        assertThat(result.get().ratioNumerator()).isEqualTo(4);
        assertThat(result.get().ratioDenominator()).isEqualTo(1);
    }

    @Test
    void rightsSubjectExtractsRatioAndPremiumPrice() {
        Optional<CorporateActionSubjectParser.ParsedSubject> result =
            CorporateActionSubjectParser.parse("Rights 6:7 @ Premium Rs 105/-");

        assertThat(result).isPresent();
        assertThat(result.get().actionType()).isEqualTo("RIGHTS");
        assertThat(result.get().ratioNumerator()).isEqualTo(6);
        assertThat(result.get().ratioDenominator()).isEqualTo(7);
        assertThat(result.get().price()).isEqualByComparingTo("105");
    }

    @Test
    void faceValueSplitExtractsRatioFromBeforeAndAfterValues() {
        Optional<CorporateActionSubjectParser.ParsedSubject> result = CorporateActionSubjectParser.parse(
            "Face Value Split (Sub-Division) - From Rs 10/- Per Share To Re 1/- Per Share"
        );

        assertThat(result).isPresent();
        assertThat(result.get().actionType()).isEqualTo("SPLIT");
        assertThat(result.get().ratioNumerator()).isEqualTo(10);
        assertThat(result.get().ratioDenominator()).isEqualTo(1);
    }

    @Test
    void buyBackSubjectClassifiesWithNoExtractableFields() {
        Optional<CorporateActionSubjectParser.ParsedSubject> result = CorporateActionSubjectParser.parse("Buy Back");

        assertThat(result).isPresent();
        assertThat(result.get().actionType()).isEqualTo("BUYBACK");
        assertThat(result.get().price()).isNull();
    }

    @Test
    void unrecognizedSubjectReturnsEmptyRatherThanGuessing() {
        // Real-shaped subject with none of the five keyword signals.
        assertThat(CorporateActionSubjectParser.parse("Change in Registrar and Share Transfer Agent")).isEmpty();
    }

    @Test
    void blankOrNullSubjectReturnsEmpty() {
        assertThat(CorporateActionSubjectParser.parse("")).isEmpty();
        assertThat(CorporateActionSubjectParser.parse(null)).isEmpty();
    }
}
