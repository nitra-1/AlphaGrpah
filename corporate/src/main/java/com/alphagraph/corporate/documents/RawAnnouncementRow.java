package com.alphagraph.corporate.documents;

/**
 * One row as parsed from NSE's real corporate-announcements JSON feed, before symbol resolution.
 * Field names mirror the source JSON's own (symbol, sm_isin, desc, attchmntText, attchmntFile,
 * an_dt, seq_id) - see {@link AnnouncementsParser}.
 */
record RawAnnouncementRow(
    String symbol, String isin, String category, String title, String pdfUrl, String announcedAt, String externalId
) {
}
