package com.alphagraph.intelligence.sectorcontext;

import java.math.BigDecimal;
import java.time.LocalDate;

/** One already-resolved metric value on one date - the common shape {@link SectorContextEngine} walks, regardless of which of the three metrics it came from. */
record DatedValue(LocalDate date, BigDecimal value) {
}
