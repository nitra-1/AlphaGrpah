/**
 * Module 1.7: the bulk/block deals pipeline. Unlike shareholding pattern (Module 1.2), NSE
 * genuinely publishes this for free and live - archives.nseindia.com/content/equities/bulk.csv
 * and .../block.csv - but the URL carries no date parameter, so it only ever exposes the CURRENT
 * day's deals; there's no historical archive to backfill from the way Module 1.5 backfilled
 * market.daily_prices. Real history accumulates day-by-day from here on. Internal wiring only;
 * the public domain type is {@link com.alphagraph.ownership.api.BulkDeal}.
 */
package com.alphagraph.ownership.deals;
