package com.alphagraph.corporate.api;

/** Which stage of an order's lifecycle one {@code corporate.order_book_ledger} row represents. */
public enum OrderLifecycleStage {
    NEW_ORDER,
    TENDER_WIN,
    EXECUTION_UPDATE,
    CANCELLATION,
    COMPLETION
}
