/**
 * Module 2.1: the Document Pipeline's OCR/Parse -> Extract -> Chunk -> Entity Extraction ->
 * Embeddings stages, run against documents {@code corporate.documents} (see
 * {@link com.alphagraph.corporate.documents}) has already collected. Calls the Python NLP sidecar
 * directly over REST via {@link com.alphagraph.corporate.processing.NlpSidecarClient} - no
 * intelligence-bridging needed, since this is an external service call, not a cross-domain-module
 * dependency (docs/001_System_Architecture.md §5).
 */
package com.alphagraph.corporate.processing;
