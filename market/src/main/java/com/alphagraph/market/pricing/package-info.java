/**
 * Module 1.1: the daily OHLC/volume/delivery pipeline — Collector reads a bundled NSE
 * sec_bhavdata_full-format CSV, Normalizer resolves symbol against reference.instruments,
 * Loader upserts into market.daily_prices. Internal wiring only; the public domain type is
 * {@link com.alphagraph.market.api.DailyPrice}.
 */
package com.alphagraph.market.pricing;
