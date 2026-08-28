package com.alphagraph.ownership.deals;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

class DealMaterialityWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DealMaterialityWriter writer = new DealMaterialityWriter(jdbcTemplate);

    @Test
    void writeIssuesExactlyOneInsert() {
        DealMaterialityResult result = new DealMaterialityResult(
            UUID.randomUUID(), "AASTHA", LocalDate.of(2026, 7, 24), new BigDecimal("50000000.00"),
            new BigDecimal("10000000.00"), new BigDecimal("5.0000"), "BUY",
            2, 2, 3, 1,
            72.5, "HIGH",
            new BigDecimal("60000000.00"), new BigDecimal("10000000.00"),
            new BigDecimal("50000000.00"), 0.7143, "STRONG_NET_BUYING",
            1, Instant.now()
        );

        writer.write(result);

        verify(jdbcTemplate, times(1)).update(anyString(), any(Object[].class));
    }
}
