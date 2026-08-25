package com.alphagraph.api.admin;

/** Response for a manual cron retry, covering both a standalone job and an ETL pipeline. */
public record CronRetryResponse(String name, String message) {
}
