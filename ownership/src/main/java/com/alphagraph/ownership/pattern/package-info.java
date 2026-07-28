/**
 * Module 1.2: the shareholding pattern pipeline. Unlike market data (Module 1.1), there's no
 * free public bulk file for this - it's filed per-company via NEAPS with bulk access behind
 * NSE's paid Corporate Data Subscription. Collector reads a manually-compiled sample CSV
 * (real percentages looked up per company where a source was found; see the pipeline class
 * javadoc for which) until a real automated source is chosen. Internal wiring only; the public
 * domain type is {@link com.alphagraph.ownership.api.ShareholdingPattern}.
 */
package com.alphagraph.ownership.pattern;
