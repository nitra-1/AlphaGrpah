/**
 * Capital Allocation evidence (Multibagger Discovery Stage 1, Tier 4 of the remaining 7 evidence
 * families) - rolling 180-day buyback/equity-raise event counts, reusing the already-live
 * {@code corporate.corporate_actions} data via a new, additive
 * {@code corporate.api.CorporateActionsReader.findAllActions(instrumentId)}. No new ingestion.
 */
package com.alphagraph.corporate.transformation;
