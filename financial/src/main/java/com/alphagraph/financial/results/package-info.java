/**
 * Module 1.3: the financial results (fundamentals) pipeline. Same real-world gap as ownership's
 * shareholding pattern (Module 1.2): there's no free public bulk file for Sales/PAT/EPS/ROE/ROCE/
 * margins/cash-flow - results are filed per-company as XBRL/PDF, with bulk access behind paid
 * data subscriptions. Collector reads a manually-compiled sample CSV (real Q4 FY25 figures looked
 * up per company where a source was found; see the pipeline class javadoc for which) until a real
 * automated source is chosen. Internal wiring only; the public domain type is
 * {@link com.alphagraph.financial.api.FinancialResult}.
 */
package com.alphagraph.financial.results;
