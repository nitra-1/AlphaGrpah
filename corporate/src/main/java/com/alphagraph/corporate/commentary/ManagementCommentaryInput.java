package com.alphagraph.corporate.commentary;

import com.alphagraph.corporate.api.ManagementObservation;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** One instrument's full observation history (newest first) - the input to {@link ManagementCommentaryEngine}. */
record ManagementCommentaryInput(UUID instrumentId, String symbol, List<ManagementObservation> observations, LocalDate asOfDate) {
}
