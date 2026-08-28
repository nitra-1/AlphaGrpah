package com.alphagraph.ownership.deals;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class MarketLiquidityReaderTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final MarketLiquidityReader reader = new MarketLiquidityReader(jdbcTemplate);

    @Test
    @SuppressWarnings("unchecked")
    void fewerThan20RealTradingSessionsReturnsEmptyNotAGuess() {
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any()))
            .thenReturn(List.of(BigDecimal.valueOf(1_000_000), BigDecimal.valueOf(2_000_000)));

        assertThat(reader.findAdtv20("AASTHA", LocalDate.of(2026, 7, 24))).isEmpty();
    }

    @Test
    @SuppressWarnings("unchecked")
    void exactly20SessionsAtTheSameTurnoverAverageToThatExactFigure() {
        // 20 sessions of exactly Rs.1,00,00,000 (1 crore) turnover each -> ADTV = exactly 1 crore,
        // the fixture the plan's own "ratio 100Cr/100Cr = exactly 1.0x" test builds on.
        List<BigDecimal> twentySessions = IntStream.range(0, 20)
            .mapToObj(i -> new BigDecimal("10000000.00"))
            .toList();
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(twentySessions);

        Optional<BigDecimal> adtv = reader.findAdtv20("AASTHA", LocalDate.of(2026, 7, 24));

        assertThat(adtv).isPresent();
        assertThat(adtv.get()).isEqualByComparingTo("10000000.00");
    }

    @Test
    @SuppressWarnings("unchecked")
    void twentySessionsWithDifferentTurnoversAverageCorrectly() {
        List<BigDecimal> sessions = List.of(
            new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("100"),
            new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("100"),
            new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("100"),
            new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("100"),
            new BigDecimal("100"), new BigDecimal("200"), new BigDecimal("300"), new BigDecimal("100")
        );
        when(jdbcTemplate.query(anyString(), any(RowMapper.class), any(), any(), any())).thenReturn(sessions);

        Optional<BigDecimal> adtv = reader.findAdtv20("AASTHA", LocalDate.of(2026, 7, 24));

        assertThat(adtv).isPresent();
        assertThat(adtv.get()).isEqualByComparingTo("175.00");
    }
}
