package com.alphagraph.ownership.pattern;

/** One row as parsed from the sample shareholding CSV, before symbol resolution. */
record RawShareholdingRow(
    String symbol, String periodEnd, String promoterPct, String fiiPct, String diiPct, String mfPct, String publicPct
) {
}
