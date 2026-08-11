package com.alphagraph.reference.instrument;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InstrumentWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final InstrumentWriter writer = new InstrumentWriter(jdbcTemplate);

    private final UUID instrumentId = UUID.randomUUID();
    private final UUID sectorId = UUID.randomUUID();

    @Test
    void updateSectorReturnsFalseWhenNoInstrumentExists() {
        when(jdbcTemplate.queryForObject(contains("reference.instruments"), eq(Long.class), eq(instrumentId))).thenReturn(0L);

        assertThat(writer.updateSector(instrumentId, sectorId)).isFalse();
        verify(jdbcTemplate, never()).update(contains("UPDATE"), eq(sectorId), eq(instrumentId));
    }

    @Test
    void updateSectorThrowsWhenTheNewSectorDoesntExist() {
        when(jdbcTemplate.queryForObject(contains("reference.instruments"), eq(Long.class), eq(instrumentId))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("reference.sectors"), eq(Long.class), eq(sectorId))).thenReturn(0L);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> writer.updateSector(instrumentId, sectorId))
            .withMessageContaining("No sector with id");
        verify(jdbcTemplate, never()).update(contains("UPDATE"), eq(sectorId), eq(instrumentId));
    }

    @Test
    void updateSectorAssignsTheNewSector() {
        when(jdbcTemplate.queryForObject(contains("reference.instruments"), eq(Long.class), eq(instrumentId))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("reference.sectors"), eq(Long.class), eq(sectorId))).thenReturn(1L);

        assertThat(writer.updateSector(instrumentId, sectorId)).isTrue();
        verify(jdbcTemplate).update(contains("UPDATE"), eq(sectorId), eq(instrumentId));
    }

    @Test
    void updateSectorClearsTheSectorWhenGivenNull() {
        when(jdbcTemplate.queryForObject(contains("reference.instruments"), eq(Long.class), eq(instrumentId))).thenReturn(1L);

        assertThat(writer.updateSector(instrumentId, null)).isTrue();
        verify(jdbcTemplate).update(contains("UPDATE"), isNull(), eq(instrumentId));
    }
}
