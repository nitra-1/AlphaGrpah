package com.alphagraph.corporate.api;

/**
 * How strongly management is committing to a forward-looking statement, from language strength -
 * "we hope" (LOW), "we expect" (MEDIUM), "we are confident" (HIGH), "orders already secured"
 * (VERY_HIGH). Distinct from extraction confidence (how sure the model is it read the statement
 * correctly) - this is a property of what management actually said, not of the extraction.
 */
public enum CommitmentLevel {
    LOW,
    MEDIUM,
    HIGH,
    VERY_HIGH
}
