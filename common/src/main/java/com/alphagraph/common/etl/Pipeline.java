package com.alphagraph.common.etl;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Executes a {@link PipelineDefinition}'s five stages in order and reports what happened.
 * Collector/Parser failures fail the whole run (there's no per-record granularity yet at that
 * point); a Validator, Normalizer, or Loader failure on one record only rejects that record —
 * per docs/002_Engine_Architecture.md §2, bad records are quarantined, not fatal.
 */
public final class Pipeline<R, T, D> {

    private final PipelineDefinition<R, T, D> definition;

    public Pipeline(PipelineDefinition<R, T, D> definition) {
        this.definition = definition;
    }

    public PipelineOutcome<T> run() {
        Instant startedAt = Instant.now();
        String pipelineName = definition.sourceConfig().name();

        List<T> parsed;
        try {
            R raw = definition.collector().fetch(definition.sourceConfig());
            parsed = definition.parser().parse(raw);
        } catch (RuntimeException e) {
            PipelineRunResult failed = new PipelineRunResult(
                pipelineName, startedAt, Instant.now(), PipelineStatus.FAILED, 0, 0, 0, List.of(String.valueOf(e.getMessage()))
            );
            return new PipelineOutcome<>(List.of(), failed);
        }

        int rowsAccepted = 0;
        int rowsRejected = 0;
        List<String> errors = new ArrayList<>();

        for (T record : parsed) {
            try {
                ValidationResult validation = definition.validator().validate(record);
                if (!validation.passed()) {
                    rowsRejected++;
                    errors.addAll(validation.errors());
                    continue;
                }
                D normalized = definition.normalizer().normalize(record);
                definition.loader().load(normalized);
                rowsAccepted++;
            } catch (RuntimeException e) {
                rowsRejected++;
                errors.add(String.valueOf(e.getMessage()));
            }
        }

        PipelineStatus status = statusFor(parsed.size(), rowsAccepted, rowsRejected);
        PipelineRunResult result = new PipelineRunResult(
            pipelineName, startedAt, Instant.now(), status, parsed.size(), rowsAccepted, rowsRejected, errors
        );
        return new PipelineOutcome<>(parsed, result);
    }

    private static PipelineStatus statusFor(int rowsRead, int rowsAccepted, int rowsRejected) {
        if (rowsRead == 0 || rowsRejected == 0) {
            return PipelineStatus.SUCCESS;
        }
        if (rowsAccepted == 0) {
            return PipelineStatus.FAILED;
        }
        return PipelineStatus.PARTIAL;
    }
}
