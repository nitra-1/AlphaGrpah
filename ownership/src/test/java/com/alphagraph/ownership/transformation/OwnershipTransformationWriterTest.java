package com.alphagraph.ownership.transformation;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

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

class OwnershipTransformationWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final OwnershipTransformationWriter writer = new OwnershipTransformationWriter(jdbcTemplate);
    private final UUID stateId = UUID.randomUUID();
    private final UUID instrumentId = UUID.randomUUID();

    @Test
    @SuppressWarnings("unchecked")
    void writeUpsertsTheStateRowAndReplacesItsReasons() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(stateId));

        OwnershipTransformationResult result = new OwnershipTransformationResult(
            instrumentId, "RELIANCE", LocalDate.of(2026, 9, 16), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 3, 31),
            TransformationState.FII_ACCUMULATION, 90.0, 1, Instant.now(),
            List.of(ReasonCode.of("FII_ACCUMULATION", 0.60))
        );

        writer.write(result);

        verify(jdbcTemplate, times(1)).query(anyString(), any(RowMapper.class), any(Object[].class));
        verify(jdbcTemplate, times(1)).update(eq("DELETE FROM ownership.transformation_state_reasons WHERE state_id = ?"), eq(stateId));
        verify(jdbcTemplate, times(1)).update(
            eq("INSERT INTO ownership.transformation_state_reasons (id, state_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)"),
            any(), eq(stateId), eq("FII_ACCUMULATION"), eq(0.60), eq((String) null)
        );
    }

    @Test
    @SuppressWarnings("unchecked")
    void reRunningTheSameDayReplacesReasonsRatherThanAccumulatingThem() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(stateId));

        OwnershipTransformationResult first = new OwnershipTransformationResult(
            instrumentId, "RELIANCE", LocalDate.of(2026, 9, 16), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 3, 31),
            TransformationState.NO_CLEAR_SIGNAL, 40.0, 1, Instant.now(), List.of()
        );
        OwnershipTransformationResult second = new OwnershipTransformationResult(
            instrumentId, "RELIANCE", LocalDate.of(2026, 9, 16), LocalDate.of(2026, 6, 30), LocalDate.of(2026, 3, 31),
            TransformationState.FII_ACCUMULATION, 90.0, 1, Instant.now(), List.of(ReasonCode.of("FII_ACCUMULATION", 0.60))
        );

        writer.write(first);
        writer.write(second);

        verify(jdbcTemplate, times(2)).update(eq("DELETE FROM ownership.transformation_state_reasons WHERE state_id = ?"), eq(stateId));
        verify(jdbcTemplate, times(1)).update(
            eq("INSERT INTO ownership.transformation_state_reasons (id, state_id, reason_code, metric_value, evidence_reference) VALUES (?, ?, ?, ?, ?)"),
            any(), eq(stateId), eq("FII_ACCUMULATION"), eq(0.60), eq((String) null)
        );
    }
}
