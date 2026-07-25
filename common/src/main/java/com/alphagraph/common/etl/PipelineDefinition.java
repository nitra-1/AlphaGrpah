package com.alphagraph.common.etl;

/**
 * Composition root for one data source's pipeline. A domain module builds one of these from the
 * Collector/Parser/Validator/Normalizer/Loader beans it registers for a source it owns; the
 * scheduler module only ever calls {@link Pipeline#run()} on it, never constructs the stages
 * itself — see docs/002_Engine_Architecture.md §2.
 */
public record PipelineDefinition<R, T, D>(
    SourceConfig sourceConfig,
    Collector<R> collector,
    Parser<R, T> parser,
    Validator<T> validator,
    Normalizer<T, D> normalizer,
    Loader<D> loader
) {
}
