package com.alphagraph.api.portfolio;

/** One domain score's move from entry to current - only included when |delta| >= 5.0 (see {@link PositionHealthClassifier}). */
record DomainDelta(String domain, double entryValue, double currentValue, double delta) {
}
