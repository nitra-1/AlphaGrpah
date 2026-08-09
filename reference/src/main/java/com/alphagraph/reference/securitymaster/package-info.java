/**
 * NSE's full listed-equity universe (~2,400 symbols), ingested from the real, free, live
 * {@code EQUITY_L.csv} feed purely as a lookup/autocomplete source for adding new tracked
 * instruments - closes the gap {@code market.pricing} disclosed at Module 1.1 ("Security Master
 * remains a one-time seed... worth revisiting if new listings/symbol changes need to flow in
 * automatically"). Deliberately separate from {@link com.alphagraph.reference.api.SecurityMasterEntry}'s
 * sibling concept in {@code reference.instruments}: a row existing here never implies the
 * instrument is tracked or scored - see docs/006_Universe_Expansion_Runbook.md for what actually
 * promotes a symbol from "known to NSE" to "tracked by AlphaGraph".
 */
package com.alphagraph.reference.securitymaster;
