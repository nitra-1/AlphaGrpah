/**
 * Module 1.4: the corporate actions pipeline. Same real-world gap as ownership (1.2) and
 * financial (1.3): NSE's bulk corporate actions report goes out over its Extranet to clearing
 * members only, not publicly - and the interactive site's per-symbol actions API is unofficial,
 * requires a browser session, and isn't a stable free bulk source either. Collector reads a
 * manually-compiled sample CSV (real dividend ex-dates/record-dates/amounts looked up per
 * company; see the pipeline class javadoc) until a real automated source is chosen. Internal
 * wiring only; the public domain type is {@link com.alphagraph.corporate.api.CorporateAction}.
 */
package com.alphagraph.corporate.actions;
