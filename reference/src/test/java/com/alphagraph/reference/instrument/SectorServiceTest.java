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

class SectorServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SectorService service = new SectorService(jdbcTemplate);

    private final UUID id = UUID.randomUUID();
    private final UUID parentId = UUID.randomUUID();

    @Test
    void createRejectsABlankName() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.create("   ", null));
    }

    @Test
    void createRejectsANullName() {
        assertThatIllegalArgumentException().isThrownBy(() -> service.create(null, null));
    }

    @Test
    void createThrowsWhenTheNameIsAlreadyTaken() {
        when(jdbcTemplate.queryForObject(contains("lower(name)"), eq(Long.class), eq("Energy"))).thenReturn(1L);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.create("Energy", null))
            .withMessageContaining("already exists");
    }

    @Test
    void createThrowsWhenTheParentDoesntExist() {
        when(jdbcTemplate.queryForObject(contains("lower(name)"), eq(Long.class), eq("New Sector"))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("WHERE id = ?"), eq(Long.class), eq(parentId))).thenReturn(0L);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.create("New Sector", parentId))
            .withMessageContaining("No sector with id");
    }

    @Test
    void createTrimsTheNameAndInsertsWithTheParent() {
        when(jdbcTemplate.queryForObject(contains("lower(name)"), eq(Long.class), eq("New Sector"))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("WHERE id = ?"), eq(Long.class), eq(parentId))).thenReturn(1L);

        UUID created = service.create("  New Sector  ", parentId);

        assertThat(created).isNotNull();
        verify(jdbcTemplate).update(contains("INSERT"), eq(created), eq("New Sector"), eq(parentId));
    }

    @Test
    void updateReturnsFalseWhenNoSectorExists() {
        when(jdbcTemplate.queryForObject(contains("WHERE id = ?"), eq(Long.class), eq(id))).thenReturn(0L);

        assertThat(service.update(id, "Renamed", null)).isFalse();
    }

    @Test
    void updateRejectsSettingASectorAsItsOwnParent() {
        when(jdbcTemplate.queryForObject(contains("WHERE id = ?"), eq(Long.class), eq(id))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("lower(name)"), eq(Long.class), eq("Renamed"), eq(id))).thenReturn(0L);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.update(id, "Renamed", id))
            .withMessageContaining("own parent");
    }

    @Test
    void updateThrowsWhenTheNewNameIsTakenByADifferentSector() {
        when(jdbcTemplate.queryForObject(contains("WHERE id = ?"), eq(Long.class), eq(id))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("lower(name)"), eq(Long.class), eq("Energy"), eq(id))).thenReturn(1L);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.update(id, "Energy", null))
            .withMessageContaining("already exists");
    }

    @Test
    void updateRenamesAndClearsTheParentWhenNoneIsGiven() {
        when(jdbcTemplate.queryForObject(contains("WHERE id = ?"), eq(Long.class), eq(id))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("lower(name)"), eq(Long.class), eq("Renamed"), eq(id))).thenReturn(0L);

        assertThat(service.update(id, "Renamed", null)).isTrue();
        verify(jdbcTemplate).update(contains("UPDATE"), eq("Renamed"), isNull(), eq(id));
    }

    @Test
    void deleteReturnsFalseWhenNoSectorExists() {
        when(jdbcTemplate.queryForObject(contains("WHERE id = ?"), eq(Long.class), eq(id))).thenReturn(0L);

        assertThat(service.delete(id)).isFalse();
    }

    @Test
    void deleteThrowsWhenInstrumentsAreStillAssigned() {
        when(jdbcTemplate.queryForObject(contains("reference.sectors WHERE id = ?"), eq(Long.class), eq(id))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("reference.instruments"), eq(Long.class), eq(id))).thenReturn(3L);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.delete(id))
            .withMessageContaining("3 instrument(s)");
        verify(jdbcTemplate, never()).update(contains("DELETE"), eq(id));
    }

    @Test
    void deleteThrowsWhenSubSectorsStillExist() {
        when(jdbcTemplate.queryForObject(contains("reference.sectors WHERE id = ?"), eq(Long.class), eq(id))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("reference.instruments"), eq(Long.class), eq(id))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("parent_sector_id = ?"), eq(Long.class), eq(id))).thenReturn(2L);

        assertThatIllegalArgumentException()
            .isThrownBy(() -> service.delete(id))
            .withMessageContaining("2 sub-sector(s)");
        verify(jdbcTemplate, never()).update(contains("DELETE"), eq(id));
    }

    @Test
    void deleteRemovesTheSectorWhenNothingReferencesIt() {
        when(jdbcTemplate.queryForObject(contains("reference.sectors WHERE id = ?"), eq(Long.class), eq(id))).thenReturn(1L);
        when(jdbcTemplate.queryForObject(contains("reference.instruments"), eq(Long.class), eq(id))).thenReturn(0L);
        when(jdbcTemplate.queryForObject(contains("parent_sector_id = ?"), eq(Long.class), eq(id))).thenReturn(0L);

        assertThat(service.delete(id)).isTrue();
        verify(jdbcTemplate).update(contains("DELETE"), eq(id));
    }
}
