package com.alphagraph.intelligence.sectorcontext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

record SectorContextEvidenceObservation(
    SectorContextMetric metric, UUID instrumentId, String symbol, LocalDate asOfDate, LocalDate priorAsOfDate,
    BigDecimal value, BigDecimal priorValue, BigDecimal change, int persistenceDays, double confidence
) {
}
