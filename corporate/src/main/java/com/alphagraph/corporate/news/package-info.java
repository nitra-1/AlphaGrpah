/**
 * Module 2.6: News Intelligence Engine. Two layers, same shape as {@code corporate.commentary}:
 * Layer 1 ({@link com.alphagraph.corporate.news.NewsLinkParser} +
 * {@code corporate.document_instrument_links}) reassembles {@code corporate.knowledge.
 * NewsExtractor}'s per-company facts into immutable links, resolving each extracted company name
 * against AlphaGraph's tracked instrument universe via
 * {@link com.alphagraph.corporate.news.NewsInstrumentMatcher} - untracked companies are simply
 * dropped, not stored as unresolved rows. Layer 2
 * ({@link com.alphagraph.corporate.news.NewsCatalystEngine}) aggregates one instrument's link
 * history into a {@code common.rules.RuleSet}-driven Catalyst Score, exactly like
 * {@code corporate.orderbook.OrderBookAggregationEngine} and
 * {@code corporate.commentary.ManagementCommentaryEngine}.
 */
package com.alphagraph.corporate.news;
