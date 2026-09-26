package com.alphagraph.corporate.news;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.reference.instrument.SectorService;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EconomicEventWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SectorService sectorService = mock(SectorService.class);
    private final CompanyResolver companyResolver = mock(CompanyResolver.class);
    private final EconomicEventWriter writer = new EconomicEventWriter(jdbcTemplate, sectorService, companyResolver);

    private static final RuleSet EMPTY_RULES = new RuleSet(1, List.of());
    private static final Instant ANNOUNCED_AT = Instant.parse("2026-09-25T10:00:00Z");

    private ParsedEconomicEvent event() {
        return new ParsedEconomicEvent("ECONOMIC", "DEFENCE_SPENDING", "POSITIVE", "HIGH", 88, "MEDIUM_TERM");
    }

    /** Counts real {@code jdbcTemplate.update(sql, ...)} invocations whose SQL text contains {@code substring} - avoids Mockito's own fragile varargs matcher syntax (confirmed live: mixing an argument matcher with a raw {@code any()}/{@code (Object[]) any()} for a varargs method produces spurious "Argument(s) are different" failures even when the real call matches). */
    private long countUpdateCallsContaining(String substring) {
        return org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("update"))
            .filter(inv -> inv.getArguments().length > 0 && inv.getArguments()[0] instanceof String sql && sql.contains(substring))
            .count();
    }

    @Test
    void documentWithNoEventGroupWritesNothing() {
        ParsedNewsDocument document = new ParsedNewsDocument(UUID.randomUUID(), null, List.of(), List.of());

        writer.write(ANNOUNCED_AT, document, EMPTY_RULES);

        assertThat(countUpdateCallsContaining("economic_events")).isZero();
    }

    @Test
    void unresolvedCompanyExposureNeverCreatesOrUpdatesADiscoveryCandidate() {
        ParsedCompanyImpact impact = new ParsedCompanyImpact(
            "Some Unknown Private Company", "POSITIVE", "signal", "summary", 60, "Defence", "DIRECT", "MEDIUM"
        );
        ParsedNewsDocument document = new ParsedNewsDocument(UUID.randomUUID(), event(), List.of(), List.of(impact));
        when(companyResolver.resolve("Some Unknown Private Company")).thenReturn(CompanyMatch.UNRESOLVED);

        writer.write(ANNOUNCED_AT, document, EMPTY_RULES);

        assertThat(countUpdateCallsContaining("economic_event_company_exposures")).isEqualTo(1);
        assertThat(countUpdateCallsContaining("news_discovery_candidates")).isZero();
    }

    @Test
    void trackedCompanyExposureNeverCreatesADiscoveryCandidateEitherAlreadyFullyTracked() {
        UUID instrumentId = UUID.randomUUID();
        ParsedCompanyImpact impact = new ParsedCompanyImpact(
            "Kaynes Technology", "POSITIVE", "signal", "summary", 90, "Electronics", "REGULATORY", "HIGH"
        );
        ParsedNewsDocument document = new ParsedNewsDocument(UUID.randomUUID(), event(), List.of(), List.of(impact));
        when(companyResolver.resolve("Kaynes Technology"))
            .thenReturn(new CompanyMatch(instrumentId, null, "KAYNES", "Kaynes Technology India Limited", "EXACT_INSTRUMENT"));

        writer.write(ANNOUNCED_AT, document, EMPTY_RULES);

        assertThat(countUpdateCallsContaining("news_discovery_candidates")).isZero();
    }

    @Test
    void resolvedUntrackedCompanyExposureUpsertsADiscoveryCandidate() {
        UUID securityMasterId = UUID.randomUUID();
        ParsedCompanyImpact impact = new ParsedCompanyImpact(
            "Kaynes Technology", "POSITIVE", "signal", "summary", 90, "Electronics", "REGULATORY", "HIGH"
        );
        ParsedNewsDocument document = new ParsedNewsDocument(UUID.randomUUID(), event(), List.of(), List.of(impact));
        when(companyResolver.resolve("Kaynes Technology"))
            .thenReturn(new CompanyMatch(null, securityMasterId, "KAYNES", "Kaynes Technology India Limited", "EXACT_SECURITY_MASTER"));

        writer.write(ANNOUNCED_AT, document, EMPTY_RULES);

        assertThat(countUpdateCallsContaining("news_discovery_candidates")).isEqualTo(1);
    }

    @Test
    void oneEventWritesBothPositiveAndNegativeSectorImpactRowsNeverCollapsed() {
        UUID sectorId1 = UUID.randomUUID();
        UUID sectorId2 = UUID.randomUUID();
        when(sectorService.findOrCreateByName("Airlines")).thenReturn(sectorId1);
        when(sectorService.findOrCreateByName("Oil Producers")).thenReturn(sectorId2);

        List<ParsedSectorImpact> sectorImpacts = List.of(
            new ParsedSectorImpact("Airlines", "NEGATIVE", "HIGH", 85, "higher fuel cost"),
            new ParsedSectorImpact("Oil Producers", "POSITIVE", "HIGH", 85, "higher realizations")
        );
        ParsedNewsDocument document = new ParsedNewsDocument(UUID.randomUUID(), event(), sectorImpacts, List.of());

        writer.write(ANNOUNCED_AT, document, EMPTY_RULES);

        assertThat(countUpdateCallsContaining("economic_event_sector_impacts")).isEqualTo(2);
    }

    @Test
    void expiryDateUsesTheDocumentsOwnAnnouncedDateNeverNow() {
        ParsedNewsDocument document = new ParsedNewsDocument(UUID.randomUUID(), event(), List.of(), List.of());

        writer.write(ANNOUNCED_AT, document, EMPTY_RULES);

        Object[] rawArgs = org.mockito.Mockito.mockingDetails(jdbcTemplate).getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("update"))
            .filter(inv -> inv.getRawArguments().length > 0 && inv.getRawArguments()[0] instanceof String sql
                && sql.contains("INSERT INTO corporate.economic_events"))
            .findFirst()
            .orElseThrow()
            .getRawArguments();
        Object[] sqlArgs = (Object[]) rawArgs[1];
        java.sql.Date expiryDate = (java.sql.Date) sqlArgs[sqlArgs.length - 1];
        // HIGH magnitude, default news-expiry-days-high = 90 (empty RuleSet -> falls back to default)
        assertThat(expiryDate.toLocalDate()).isEqualTo(java.time.LocalDate.of(2026, 9, 25).plusDays(90));
    }
}
