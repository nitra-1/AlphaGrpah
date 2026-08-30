package com.alphagraph.ownership.interpretation;

import java.time.LocalDate;

/** The most recent prior interpretation's state - all the orchestrator needs to resolve today's anchor. Absent when a symbol has never been interpreted before. */
record PriorInterpretation(InstitutionalState institutionalState, LocalDate eventAnchorDate) {
}
