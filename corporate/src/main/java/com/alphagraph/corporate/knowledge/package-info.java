/**
 * Module 2.2 (revived): Document Intelligence & Knowledge Extraction Engine. Runs ONE canonical
 * Claude API call per document - reading corporate.documents.extracted_text (Module 2.1's output)
 * - producing structured facts, topics, and a document-level summary/sentiment, stored in
 * corporate.document_facts/document_topics/document_summary. Every downstream engine (Corporate
 * Event Engine, Order Book Engine, and future Management Commentary/News engines) reads this
 * canonical output instead of calling an LLM itself - one extraction pass, many deterministic
 * rule-engine consumers, rather than each engine re-reading the same PDF text and potentially
 * extracting inconsistent values for the same fact.
 */
package com.alphagraph.corporate.knowledge;
