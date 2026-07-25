package com.alphagraph.common.etl;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PipelineTest {

    private record RawRow(String symbol, String price) {
    }

    private record DomainRow(String symbol, double price) {
    }

    private static PipelineDefinition<List<String[]>, RawRow, DomainRow> definition(
        List<String[]> rawRows, List<DomainRow> sink
    ) {
        SourceConfig sourceConfig = new SourceConfig("test-source", "common");

        Collector<List<String[]>> collector = config -> rawRows;

        Parser<List<String[]>, RawRow> parser = raw -> raw.stream()
            .map(row -> new RawRow(row[0], row.length > 1 ? row[1] : null))
            .toList();

        Validator<RawRow> validator = new RequiredFieldsValidator<>(Map.of(
            "symbol", RawRow::symbol,
            "price", RawRow::price
        ));

        Normalizer<RawRow, DomainRow> normalizer = row -> new DomainRow(row.symbol(), Double.parseDouble(row.price()));

        Loader<DomainRow> loader = sink::add;

        return new PipelineDefinition<>(sourceConfig, collector, parser, validator, normalizer, loader);
    }

    @Test
    void allValidRowsProduceSuccess() {
        List<DomainRow> sink = new ArrayList<>();
        List<String[]> rawRows = List.of(new String[] {"NSE:BEML", "1500.5"}, new String[] {"NSE:ACE", "620.0"});

        PipelineOutcome<RawRow> outcome = new Pipeline<>(definition(rawRows, sink)).run();
        PipelineRunResult result = outcome.result();

        assertThat(result.status()).isEqualTo(PipelineStatus.SUCCESS);
        assertThat(result.rowsRead()).isEqualTo(2);
        assertThat(result.rowsAccepted()).isEqualTo(2);
        assertThat(result.rowsRejected()).isZero();
        assertThat(result.errors()).isEmpty();
        assertThat(sink).containsExactly(new DomainRow("NSE:BEML", 1500.5), new DomainRow("NSE:ACE", 620.0));
        assertThat(outcome.parsedRecords()).containsExactly(
            new RawRow("NSE:BEML", "1500.5"), new RawRow("NSE:ACE", "620.0")
        );
    }

    @Test
    void rowsMissingRequiredFieldsAreQuarantinedNotFatal() {
        List<DomainRow> sink = new ArrayList<>();
        List<String[]> rawRows = List.of(
            new String[] {"NSE:BEML", "1500.5"},
            new String[] {"NSE:INCOMPLETE"} // missing price
        );

        PipelineRunResult result = new Pipeline<>(definition(rawRows, sink)).run().result();

        assertThat(result.status()).isEqualTo(PipelineStatus.PARTIAL);
        assertThat(result.rowsRead()).isEqualTo(2);
        assertThat(result.rowsAccepted()).isEqualTo(1);
        assertThat(result.rowsRejected()).isEqualTo(1);
        assertThat(result.errors()).containsExactly("price is required");
        assertThat(sink).containsExactly(new DomainRow("NSE:BEML", 1500.5));
    }

    @Test
    void allInvalidRowsProduceFailed() {
        List<DomainRow> sink = new ArrayList<>();
        List<String[]> rawRows = List.<String[]>of(new String[] {"NSE:INCOMPLETE"});

        PipelineRunResult result = new Pipeline<>(definition(rawRows, sink)).run().result();

        assertThat(result.status()).isEqualTo(PipelineStatus.FAILED);
        assertThat(result.rowsAccepted()).isZero();
        assertThat(result.rowsRejected()).isEqualTo(1);
        assertThat(sink).isEmpty();
    }

    @Test
    void collectorFailureFailsTheWholeRunWithoutThrowing() {
        SourceConfig sourceConfig = new SourceConfig("broken-source", "common");
        Collector<List<String[]>> brokenCollector = config -> {
            throw new IllegalStateException("upstream unavailable");
        };
        PipelineDefinition<List<String[]>, RawRow, DomainRow> definition = new PipelineDefinition<>(
            sourceConfig, brokenCollector, raw -> List.of(), r -> ValidationResult.valid(),
            r -> new DomainRow(r.symbol(), 0), d -> { }
        );

        PipelineOutcome<RawRow> outcome = new Pipeline<>(definition).run();
        PipelineRunResult result = outcome.result();

        assertThat(result.status()).isEqualTo(PipelineStatus.FAILED);
        assertThat(result.rowsRead()).isZero();
        assertThat(result.errors()).containsExactly("upstream unavailable");
        assertThat(outcome.parsedRecords()).isEmpty();
    }

    @Test
    void requiredFieldsValidatorFlagsEachMissingFieldByName() {
        RequiredFieldsValidator<RawRow> validator = new RequiredFieldsValidator<>(Map.of(
            "symbol", RawRow::symbol,
            "price", RawRow::price
        ));

        ValidationResult result = validator.validate(new RawRow(null, null));

        assertThat(result.passed()).isFalse();
        assertThat(result.errors()).containsExactlyInAnyOrder("symbol is required", "price is required");
    }
}
