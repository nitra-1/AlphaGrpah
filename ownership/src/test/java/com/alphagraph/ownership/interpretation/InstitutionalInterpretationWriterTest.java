package com.alphagraph.ownership.interpretation;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstitutionalInterpretationWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final InstitutionalInterpretationWriter writer = new InstitutionalInterpretationWriter(jdbcTemplate);
    private final UUID interpretationId = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void writeUpsertsTheRowAndReplacesItsReasons() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(interpretationId));

        InstitutionalInterpretationResult result = new InstitutionalInterpretationResult(
            "AASTHA", LocalDate.of(2026, 8, 27), EventStructure.INSTITUTIONAL_BUYING_CANDIDATE,
            InstitutionalState.POSSIBLE_ACCUMULATION, DiscoveryConfirmationState.PARTIALLY_CONFIRMED, false,
            LocalDate.of(2026, 8, 24), 2, new BigDecimal("60.00"), new BigDecimal("65.00"), new BigDecimal("50.00"),
            new BigDecimal("70.00"), new BigDecimal("50.00"), new BigDecimal("100.00"), 78.5, 82.0, "NET_BUYING",
            ChurnState.DIRECTIONAL, new BigDecimal("50000000"), BigDecimal.ZERO, 1, 0, 1, Instant.now(),
            List.of(ReasonCode.of("VERY_HIGH_MATERIALITY", 92.0))
        );

        writer.write(result);

        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
        verify(jdbcTemplate, times(1)).update(eq("DELETE FROM ownership.institutional_interpretation_reasons WHERE interpretation_id = ?"), eq(interpretationId));
        verify(jdbcTemplate, times(1)).update(
            eq("INSERT INTO ownership.institutional_interpretation_reasons (id, interpretation_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)"),
            any(), eq(interpretationId), eq("VERY_HIGH_MATERIALITY"), eq(92.0), eq((String) null)
        );
    }
}
