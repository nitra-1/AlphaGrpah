package com.alphagraph.ownership.interpretation;

/** One persisted, human-readable piece of evidence backing an interpretation - never store a conclusion without its reasons. */
record ReasonCode(String code, Double metricValue, String evidenceReference) {

    static ReasonCode of(String code) {
        return new ReasonCode(code, null, null);
    }

    static ReasonCode of(String code, double metricValue) {
        return new ReasonCode(code, metricValue, null);
    }

    static ReasonCode of(String code, double metricValue, String evidenceReference) {
        return new ReasonCode(code, metricValue, evidenceReference);
    }
}
