/**
 * Module 2.6: live collection of general news (business news, government notifications, industry
 * and sector developments) from public RSS feeds - Economic Times Markets, LiveMint Markets, and
 * PIB (Press Information Bureau, English). Same Collector/Parser/Validator/Normalizer/Loader ETL
 * shape as every prior source (see {@link com.alphagraph.corporate.documents} for the closest
 * precedent), with two real differences: (1) {@link com.alphagraph.corporate.newsfeed.NewsFeedLoader}
 * goes straight to PROCESSED status - there's no PDF to download or OCR, the article text arrives
 * directly from the feed, and nothing downstream currently reads
 * {@code corporate.document_chunks}/{@code document_entities} anyway (the three-stage extraction
 * pipeline reads {@code documents.extracted_text} directly); (2) the resulting documents carry a
 * NULL {@code instrument_id} - which company(ies) a news item affects is determined later, by
 * {@code corporate.knowledge.NewsExtractor} and {@code corporate.news.NewsInstrumentMatcher}, not
 * at collection time.
 */
package com.alphagraph.corporate.newsfeed;
