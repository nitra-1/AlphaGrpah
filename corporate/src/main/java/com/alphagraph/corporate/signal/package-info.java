/**
 * Module 2.8: Corporate Signal Engine. Combines {@code corporate.orderbook}/{@code commentary}/
 * {@code news}/{@code events}' already-computed outputs into one {@link
 * com.alphagraph.corporate.api.CorporateScore} per instrument - the roadmap's own worked example
 * ("Order Win + Positive Guidance + Capacity Expansion + Strong Demand = Corporate Score") names
 * one ingredient from each domain. Deliberately narrower than Phase 1's originally-planned, never
 * built Scoring/Decision Engine (which would also blend in Technical/Fundamental/Institutional/
 * Sector/Risk) - this module only combines corporate-domain signals.
 *
 * <p>{@link com.alphagraph.corporate.signal.CorporateSignalOrchestrator} only computes a score for
 * instruments with at least one corporate engine's output on record (the union of instrument_ids
 * across the four upstream tables), not every tracked instrument unconditionally - an instrument
 * with zero corporate history would only ever produce a meaningless neutral score.
 */
package com.alphagraph.corporate.signal;
