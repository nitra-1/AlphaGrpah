package com.alphagraph.corporate.newsfeed;

/**
 * One {@code <item>} as parsed from an RSS 2.0 feed. {@code link} doubles as the natural upsert
 * key (RSS {@code guid} was identical to {@code link} in every real feed verified for this
 * module) - no separate guid field. {@code description}/{@code pubDate} can be empty - PIB's feed
 * carries only title/link per item, a real, disclosed format difference from Economic
 * Times/LiveMint's fuller items.
 */
record RawNewsItem(String outlet, String title, String link, String description, String pubDate) {
}
