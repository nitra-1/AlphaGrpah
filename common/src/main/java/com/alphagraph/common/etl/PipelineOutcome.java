package com.alphagraph.common.etl;

import java.util.List;

/**
 * What {@link Pipeline#run()} actually returns: the run summary plus every record that was
 * parsed (valid and invalid alike). A caller needs the parsed batch, not just the summary, to
 * run the Data Quality Engine afterward — see docs/002_Engine_Architecture.md §3, which scores
 * completeness and duplicates over the whole batch, not just the accepted rows.
 */
public record PipelineOutcome<T>(List<T> parsedRecords, PipelineRunResult result) {

    public PipelineOutcome {
        parsedRecords = List.copyOf(parsedRecords);
    }
}
