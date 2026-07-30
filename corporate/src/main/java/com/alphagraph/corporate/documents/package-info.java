/**
 * Module 2.1: the Document Collection Framework's first real source - NSE's real, live, free
 * corporate-announcements feed (verified live at
 * https://www.nseindia.com/api/corporate-announcements; PDF attachments served freely from
 * nsearchives.nseindia.com, no session cookie needed for those). Same
 * Collector/Parser/Validator/Normalizer/Loader ETL shape as every Phase 1 source, with one new
 * responsibility: {@link com.alphagraph.corporate.documents.AnnouncementsLoader} also performs
 * the pipeline's "Download" stage (fetching and storing the actual PDF bytes), not just metadata.
 * See {@link com.alphagraph.corporate.processing} for the later OCR/Parse -> Extract -> Chunk ->
 * Entity Extraction -> Embeddings stages, which run against already-collected documents.
 */
package com.alphagraph.corporate.documents;
