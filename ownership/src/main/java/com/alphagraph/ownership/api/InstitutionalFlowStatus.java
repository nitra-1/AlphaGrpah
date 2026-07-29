package com.alphagraph.ownership.api;

/** Shared by FII, DII, and MF - each is tracked separately but classified the same way. */
public enum InstitutionalFlowStatus {
    BUYING,
    STABLE,
    SELLING
}
