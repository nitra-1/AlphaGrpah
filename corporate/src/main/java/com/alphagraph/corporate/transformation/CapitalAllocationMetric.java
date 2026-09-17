package com.alphagraph.corporate.transformation;

enum CapitalAllocationMetric {
    /** Rolling 180-day count of real BUYBACK actions. */
    BUYBACK_EVENT_COUNT_180D,
    /** Rolling 180-day count of real RIGHTS actions - a neutral capital-raise signal, not a dilution judgment (see V15 migration comment). */
    EQUITY_RAISE_EVENT_COUNT_180D
}
