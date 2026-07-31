/**
 * Module 2.3: Corporate Event Engine. Classifies already-processed document text
 * (corporate.documents.extracted_text, written by {@link com.alphagraph.corporate.processing})
 * into zero or more of 13 named corporate events via a real Claude API call - the first Corporate
 * Intelligence engine, and architecturally distinct from every Phase 1 engine: those score clean
 * numeric metrics via deterministic {@code common.rules.RuleSet} threshold rules, this one
 * classifies free text via genuine semantic understanding. Never re-parses PDFs at extraction
 * time - it reads text already produced by the Document Pipeline, per
 * docs/claude.md's "Never parse documents directly during scoring" principle.
 */
package com.alphagraph.corporate.events;
