package com.alphagraph.ownership.interpretation;

/** One participant's repeat-activity shape within a window - feeds reason codes only, never persisted as its own column. */
enum RepeatBehavior {
    ONE_OFF,
    REPEAT_BUYER,
    REPEAT_SELLER,
    PERSISTENT_BUYER,
    PERSISTENT_SELLER
}
