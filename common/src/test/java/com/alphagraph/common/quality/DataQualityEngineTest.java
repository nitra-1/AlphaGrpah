package com.alphagraph.common.quality;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class DataQualityEngineTest {

    private record Row(String symbol, String price, String isin) {
    }

    private static DataQualitySpec<Row> spec() {
        return new DataQualitySpec<>(
            Map.of("symbol", Row::symbol, "price", Row::price, "isin", Row::isin),
            Set.of("symbol", "price"),
            Row::symbol
        );
    }

    private final DataQualityEngine engine = new DataQualityEngine();

    @Test
    void perfectBatchScoresOne() {
        List<Row> rows = List.of(
            new Row("NSE:BEML", "1500.5", "INE258A01016"),
            new Row("NSE:ACE", "620.0", "INE731H01025")
        );

        DataQualityScore result = engine.score(DataQualityInput.from(rows, spec(), 0));

        assertThat(result.completeness()).isEqualTo(1.0);
        assertThat(result.duplicateRate()).isZero();
        assertThat(result.missingFieldRate()).isZero();
        assertThat(result.validationErrorRate()).isZero();
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void missingOptionalFieldReducesCompletenessButNotMissingFieldRate() {
        List<Row> rows = List.of(new Row("NSE:BEML", "1500.5", null));

        DataQualityScore result = engine.score(DataQualityInput.from(rows, spec(), 0));

        assertThat(result.completeness()).isCloseTo(2.0 / 3.0, within(1e-9));
        assertThat(result.missingFieldRate()).isZero();
    }

    @Test
    void missingRequiredFieldIncreasesMissingFieldRate() {
        List<Row> rows = List.of(
            new Row("NSE:BEML", null, "INE258A01016"),
            new Row("NSE:ACE", "620.0", "INE731H01025")
        );

        DataQualityScore result = engine.score(DataQualityInput.from(rows, spec(), 0));

        // 1 missing out of 4 required-field slots (2 rows x 2 required fields).
        assertThat(result.missingFieldRate()).isCloseTo(0.25, within(1e-9));
    }

    @Test
    void duplicateNaturalKeysIncreaseDuplicateRate() {
        List<Row> rows = List.of(
            new Row("NSE:BEML", "1500.5", "INE258A01016"),
            new Row("NSE:BEML", "1501.0", "INE258A01016"),
            new Row("NSE:ACE", "620.0", "INE731H01025")
        );

        DataQualityScore result = engine.score(DataQualityInput.from(rows, spec(), 0));

        // Both NSE:BEML rows collide on the natural key, ACE is unique.
        assertThat(result.duplicateRate()).isCloseTo(2.0 / 3.0, within(1e-9));
    }

    @Test
    void validationErrorsAreReflectedDirectlyInTheRate() {
        List<Row> rows = List.of(new Row("NSE:BEML", "1500.5", "INE258A01016"));

        DataQualityScore result = engine.score(DataQualityInput.from(rows, spec(), 1));

        assertThat(result.validationErrorRate()).isEqualTo(1.0);
    }

    @Test
    void emptyBatchIsVacuouslyPerfect() {
        DataQualityScore result = engine.score(DataQualityInput.from(List.of(), spec(), 0));

        assertThat(result.completeness()).isEqualTo(1.0);
        assertThat(result.duplicateRate()).isZero();
        assertThat(result.missingFieldRate()).isZero();
        assertThat(result.validationErrorRate()).isZero();
        assertThat(result.score()).isEqualTo(1.0);
    }

    @Test
    void worstCaseBatchScoresVeryLow() {
        // Shared key (duplicateRate 1), every other field missing (missingFieldRate 0.5,
        // since the key field itself is the only required field still present), every row
        // rejected (validationErrorRate 1).
        List<Row> rows = List.of(
            new Row("DUP", null, null),
            new Row("DUP", null, null)
        );

        DataQualityScore result = engine.score(DataQualityInput.from(rows, spec(), 2));

        assertThat(result.duplicateRate()).isEqualTo(1.0);
        assertThat(result.missingFieldRate()).isEqualTo(0.5);
        assertThat(result.validationErrorRate()).isEqualTo(1.0);
        assertThat(result.score()).isLessThan(0.25);
    }

    @Test
    void nullNaturalKeyIsSkippedRatherThanCountedAsADuplicate() {
        List<Row> rows = List.of(
            new Row(null, "1500.5", "INE258A01016"),
            new Row(null, "620.0", "INE731H01025")
        );

        DataQualityScore result = engine.score(DataQualityInput.from(rows, spec(), 0));

        assertThat(result.duplicateRate()).isZero();
    }
}
