package com.alphagraph.corporate.newsfeed;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class NewsRelevanceFilterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final NewsRelevanceFilter filter = new NewsRelevanceFilter(jdbcTemplate);

    private void seedInstruments(List<String> symbols, List<String> names) {
        when(jdbcTemplate.queryForList(contains("symbol"), eq(String.class))).thenReturn(symbols);
        when(jdbcTemplate.queryForList(contains("name"), eq(String.class))).thenReturn(names);
    }

    @Test
    void matchesOnTrackedSymbol() {
        seedInstruments(List.of("TCS", "INFY"), List.of("Tata Consultancy Services Ltd", "Infosys Ltd"));

        assertThat(filter.isRelevant("TCS reports strong Q1 results beating estimates")).isTrue();
    }

    @Test
    void matchesOnCompanyNameWithLegalSuffixStripped() {
        seedInstruments(List.of("TCS", "INFY"), List.of("Tata Consultancy Services Ltd", "Infosys Ltd"));

        assertThat(filter.isRelevant("Infosys announced a new multi-year deal with a European client")).isTrue();
    }

    @Test
    void caseInsensitiveMatch() {
        seedInstruments(List.of("TCS"), List.of("Tata Consultancy Services Ltd"));

        assertThat(filter.isRelevant("tcs shares rallied in early trade")).isTrue();
    }

    @Test
    void noMatchReturnsFalse() {
        seedInstruments(List.of("TCS", "INFY"), List.of("Tata Consultancy Services Ltd", "Infosys Ltd"));

        assertThat(filter.isRelevant("The government announced new import tariffs on steel")).isFalse();
    }

    @Test
    void shortSymbolDoesNotMatchInsideAnUnrelatedWord() {
        // ACE is both a real tracked symbol and a substring of ordinary English words - whole-word
        // matching must not treat "faced"/"space" as a mention of the ACE stock.
        seedInstruments(List.of("ACE"), List.of("Action Construction Equipment Limited"));

        assertThat(filter.isRelevant("The minister faced tough questions in a crowded space")).isFalse();
    }

    @Test
    void shortSymbolStillMatchesAsAWholeWord() {
        seedInstruments(List.of("ACE"), List.of("Action Construction Equipment Limited"));

        assertThat(filter.isRelevant("ACE Limited won a new tender for construction equipment")).isTrue();
    }

    @Test
    void blankTextIsNeverRelevant() {
        seedInstruments(List.of("TCS"), List.of("Tata Consultancy Services Ltd"));

        assertThat(filter.isRelevant("")).isFalse();
        assertThat(filter.isRelevant(null)).isFalse();
    }
}
