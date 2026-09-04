/**
 * Module 1.4: the corporate actions pipeline. The original real-world-gap assessment (NSE's bulk
 * corporate actions report only going out over its Extranet to clearing members) turned out to be
 * outdated - {@code https://www.nseindia.com/api/corporates-corporateActions} is a real, free,
 * live, whole-market JSON feed (confirmed live 2026-09-03, same {@code nseindia.com/api/*}
 * anti-bot-cookie-handshake family as this module's own corporate-announcements feed). Now sourced
 * live via {@link com.alphagraph.corporate.actions.HttpCorporateActionsCollector}, active in
 * {@code local}/{@code docker}/{@code prod}; {@link com.alphagraph.corporate.actions.CorporateActionsCollector}
 * (a bundled sample) is only the fallback for a profile with no live source wired. Internal wiring
 * only; the public domain type is {@link com.alphagraph.corporate.api.CorporateAction}.
 */
package com.alphagraph.corporate.actions;
