/**
 * The runtime write path for tracked instruments - "Add Instrument", per
 * docs/006_Universe_Expansion_Runbook.md, backing the admin form a non-technical hire uses.
 * Deliberately distinct from {@code reference.securitymaster}: that package only knows what NSE
 * lists; this package is what actually promotes a symbol into {@code reference.instruments},
 * where it becomes scored/tracked.
 */
package com.alphagraph.reference.instrument;
