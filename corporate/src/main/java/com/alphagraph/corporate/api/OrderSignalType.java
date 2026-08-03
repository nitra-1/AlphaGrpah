package com.alphagraph.corporate.api;

/**
 * One of the dashboard-facing Order Book signals. MARGIN_IMPROVING (named in the roadmap) is
 * deliberately absent - no margin data is extracted anywhere in this pipeline yet, so deriving it
 * from order-value trends alone would be a real stretch rather than a genuine signal; a disclosed
 * gap, same pattern as Risk Engine's absent Event Risk domain (Module 1.9).
 */
public enum OrderSignalType {
    LARGE_ORDER,
    REPEAT_CUSTOMER,
    EXECUTION_DELAY,
    ORDER_CANCELLATION
}
