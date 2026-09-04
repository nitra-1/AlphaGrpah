package com.alphagraph.corporate.actions;

/**
 * One row as parsed from NSE's real corporate-actions JSON feed, before symbol resolution and
 * before {@code subject} is classified into an {@code action_type} (see
 * {@link CorporateActionSubjectParser}) - NSE's own feed carries a free-text description, not a
 * pre-classified type.
 */
record RawCorporateActionRow(String symbol, String subject, String exDate, String recordDate, String announcementDate) {
}
