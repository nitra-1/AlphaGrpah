package com.alphagraph.ownership.pattern;

/**
 * One row as parsed from NSE's real per-symbol shareholding JSON (or the bundled sample reshaped
 * to match it), before symbol resolution. {@code fiiPct}/{@code diiPct}/{@code mfPct} come back
 * null for a live-derived row - the summary JSON only carries promoter/public directly; FII/DII/MF
 * are derived later from the linked XBRL filing (see {@code XbrlEnrichmentOrchestrator}).
 * {@code xbrlUrl} is carried here only to be captured by {@link ShareholdingXbrlUrlWriter} - it
 * never reaches {@code ownership.api.ShareholdingPattern}.
 */
record RawShareholdingRow(
    String symbol, String periodEnd, String promoterPct, String fiiPct, String diiPct, String mfPct,
    String publicPct, String xbrlUrl
) {
}
