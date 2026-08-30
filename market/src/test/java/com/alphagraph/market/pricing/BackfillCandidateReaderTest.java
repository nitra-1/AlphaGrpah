package com.alphagraph.market.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class BackfillCandidateReaderTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final BackfillCandidateReader reader = new BackfillCandidateReader(jdbcTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void findSymbolsNeedingBackfillReturnsEachSymbolWithItsOwnTargetBeforeDate() {
        LocalDate today = LocalDate.of(2026, 8, 30);
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), eq(20)))
            .thenReturn(List.of(
                new BackfillTarget("LENSKART", LocalDate.of(2026, 8, 28)),
                new BackfillTarget("AASTHA", today)
            ));

        List<BackfillTarget> targets = reader.findSymbolsNeedingBackfill(20, today);

        assertThat(targets).containsExactly(
            new BackfillTarget("LENSKART", LocalDate.of(2026, 8, 28)),
            new BackfillTarget("AASTHA", today)
        );
    }

    @Test
    void findExistingTradeDatesReturnsEmptyMapWithoutQueryingForAnEmptySymbolList() {
        Map<String, Set<LocalDate>> result = reader.findExistingTradeDates(List.of());

        assertThat(result).isEmpty();
        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    @SuppressWarnings("unchecked")
    void findExistingTradeDatesGroupsRowsBySymbol() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(Object[].class)))
            .thenReturn(List.of(
                new Object[] {"AASTHA", LocalDate.of(2026, 7, 20)},
                new Object[] {"AASTHA", LocalDate.of(2026, 7, 21)},
                new Object[] {"SUNSHINE", LocalDate.of(2026, 7, 21)}
            ));

        Map<String, Set<LocalDate>> result = reader.findExistingTradeDates(List.of("AASTHA", "SUNSHINE"));

        assertThat(result.get("AASTHA")).containsExactlyInAnyOrder(LocalDate.of(2026, 7, 20), LocalDate.of(2026, 7, 21));
        assertThat(result.get("SUNSHINE")).containsExactly(LocalDate.of(2026, 7, 21));
    }
}
