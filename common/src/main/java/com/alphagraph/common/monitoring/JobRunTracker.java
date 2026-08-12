package com.alphagraph.common.monitoring;

/**
 * Records a scheduled job's execution for the admin monitoring view - deliberately just an
 * interface here (no Spring/JDBC types), matching {@code common.etl}'s existing
 * Collector/Parser/Loader pattern: every domain module already depends on {@code common} and can
 * reference this type, while the one real {@code @Component} implementation (backed by
 * {@code common.job_runs}) lives in the {@code scheduler} module, which already owns the parallel
 * {@code scheduler.pipeline_executions} tracking for the registered ETL pipelines.
 */
public interface JobRunTracker {

    /** For a job with no single representative output table to summarize against. */
    void run(String jobName, Runnable work);

    /**
     * @param summaryTable a schema-qualified table name (e.g. "financial.fundamental_scores") -
     *     must be a hardcoded constant from the calling scheduler, never external input
     * @param summaryTimestampColumn the column reflecting when a row was written (e.g. "computed_at")
     */
    void run(String jobName, Runnable work, String summaryTable, String summaryTimestampColumn);
}
