package com.alphagraph.market.pricing;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DiscoveryCandidateLookupTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DiscoveryCandidateLookup lookup = new DiscoveryCandidateLookup(jdbcTemplate);

    @Test
    void trueWhenSymbolIsAKnownDiscoveryCandidate() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Boolean.class), eq("AASTHA"))).thenReturn(true);

        assertThat(lookup.isCandidate("AASTHA")).isTrue();
    }

    @Test
    void falseWhenSymbolIsNotAKnownDiscoveryCandidate() {
        when(jdbcTemplate.queryForObject(any(String.class), eq(Boolean.class), eq("RANDOM"))).thenReturn(false);

        assertThat(lookup.isCandidate("RANDOM")).isFalse();
    }
}
