package com.alphagraph.ownership.interpretation;

/** {@link ParticipantClassifier}'s output - a type is never returned without its confidence and source. */
record ParticipantClassification(ParticipantType type, double confidence, String source) {
}
