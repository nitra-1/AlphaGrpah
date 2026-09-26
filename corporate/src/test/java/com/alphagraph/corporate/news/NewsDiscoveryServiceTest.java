package com.alphagraph.corporate.news;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsDiscoveryServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NewsDiscoveryService service = new NewsDiscoveryService(jdbcTemplate);

    @Test
    void observeReturnsTrueWhenANewCandidateWasFlippedToUnderObservation() {
        when(jdbcTemplate.update(any(String.class), eq("KAYNES"))).thenReturn(1);

        assertThat(service.observe("KAYNES")).isTrue();
    }

    @Test
    void observeReturnsFalseWhenAlreadyPastNewOrUnknown() {
        when(jdbcTemplate.update(any(String.class), eq("KAYNES"))).thenReturn(0);

        assertThat(service.observe("KAYNES")).isFalse();
    }

    @Test
    void dismissReturnsTrueWhenARowWasActuallyDismissed() {
        when(jdbcTemplate.update(any(String.class), eq("KAYNES"))).thenReturn(1);

        assertThat(service.dismiss("KAYNES")).isTrue();
    }

    @Test
    void dismissReturnsFalseWhenAlreadyDismissedOrUnknown() {
        when(jdbcTemplate.update(any(String.class), eq("KAYNES"))).thenReturn(0);

        assertThat(service.dismiss("KAYNES")).isFalse();
    }

    @Test
    void markPromotedReturnsTrueWhenARowWasActuallyUpdated() {
        when(jdbcTemplate.update(any(String.class), eq("KAYNES"))).thenReturn(1);

        assertThat(service.markPromoted("KAYNES")).isTrue();
    }

    @Test
    void markPromotedReturnsFalseForASymbolThatWasNeverANewsDiscoveryCandidate() {
        when(jdbcTemplate.update(any(String.class), eq("RELIANCE"))).thenReturn(0);

        assertThat(service.markPromoted("RELIANCE")).isFalse();
    }
}
