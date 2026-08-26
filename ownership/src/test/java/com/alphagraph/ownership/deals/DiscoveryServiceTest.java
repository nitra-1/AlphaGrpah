package com.alphagraph.ownership.deals;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DiscoveryService service = new DiscoveryService(jdbcTemplate);

    @Test
    void discardReturnsTrueWhenARowWasActuallyDismissed() {
        when(jdbcTemplate.update(any(String.class), eq("AASTHA"))).thenReturn(1);

        assertThat(service.discard("AASTHA")).isTrue();
    }

    @Test
    void discardReturnsFalseWhenAlreadyDismissedOrUnknown() {
        when(jdbcTemplate.update(any(String.class), eq("AASTHA"))).thenReturn(0);

        assertThat(service.discard("AASTHA")).isFalse();
    }
}
