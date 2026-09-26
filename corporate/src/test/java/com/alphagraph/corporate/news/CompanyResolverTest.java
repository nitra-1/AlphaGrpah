package com.alphagraph.corporate.news;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CompanyResolverTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final CompanyResolver resolver = new CompanyResolver(jdbcTemplate);

    private static final UUID TCS_INSTRUMENT_ID = UUID.randomUUID();
    private static final UUID KAYNES_SECURITY_MASTER_ID = UUID.randomUUID();
    private static final UUID BEL_SECURITY_MASTER_ID = UUID.randomUUID();
    private static final UUID ADANI_ENTERPRISES_ID = UUID.randomUUID();
    private static final UUID ADANI_PORTS_ID = UUID.randomUUID();

    @SuppressWarnings("unchecked")
    private void stubInstruments(Object[]... rows) {
        when(jdbcTemplate.query(contains("FROM reference.instruments"), any(RowMapper.class)))
            .thenAnswer(inv -> mapRows((RowMapper<Object>) inv.getArgument(1), rows));
    }

    @SuppressWarnings("unchecked")
    private void stubSecurityMaster(Object[]... rows) {
        when(jdbcTemplate.query(contains("FROM reference.security_master"), any(RowMapper.class)))
            .thenAnswer(inv -> mapRows((RowMapper<Object>) inv.getArgument(1), rows));
    }

    private void stubNoAliases() {
        when(jdbcTemplate.query(contains("news_company_aliases"), any(RowMapper.class), anyString())).thenReturn(List.of());
    }

    private void stubAlias(String symbol) {
        when(jdbcTemplate.query(contains("news_company_aliases"), any(RowMapper.class), anyString())).thenReturn(List.of(symbol));
    }

    private static List<Object> mapRows(RowMapper<Object> mapper, Object[]... rows) {
        List<Object> result = new java.util.ArrayList<>();
        for (Object[] row : rows) {
            try {
                java.sql.ResultSet rs = mock(java.sql.ResultSet.class);
                when(rs.getObject("id")).thenReturn(row[0]);
                when(rs.getString("symbol")).thenReturn((String) row[1]);
                when(rs.getString("name")).thenReturn(row.length > 2 ? (String) row[2] : null);
                when(rs.getString("company_name")).thenReturn(row.length > 2 ? (String) row[2] : null);
                result.add(mapper.mapRow(rs, 0));
            } catch (java.sql.SQLException e) {
                throw new RuntimeException(e);
            }
        }
        return result;
    }

    @Test
    void exactInstrumentNameMatch() {
        stubInstruments(new Object[]{TCS_INSTRUMENT_ID, "TCS", "Tata Consultancy Services Limited"});
        stubSecurityMaster();
        stubNoAliases();

        CompanyMatch match = resolver.resolve("Tata Consultancy Services Limited");

        assertThat(match.matchType()).isEqualTo("EXACT_INSTRUMENT");
        assertThat(match.matchedInstrumentId()).isEqualTo(TCS_INSTRUMENT_ID);
        assertThat(match.matchedSecurityMasterId()).isNull();
    }

    @Test
    void exactInstrumentSymbolMatch() {
        stubInstruments(new Object[]{TCS_INSTRUMENT_ID, "TCS", "Tata Consultancy Services Limited"});
        stubSecurityMaster();
        stubNoAliases();

        CompanyMatch match = resolver.resolve("TCS");

        assertThat(match.matchType()).isEqualTo("EXACT_INSTRUMENT");
        assertThat(match.matchedInstrumentId()).isEqualTo(TCS_INSTRUMENT_ID);
    }

    @Test
    void exactSecurityMasterMatchForUntrackedCompany() {
        stubInstruments();
        stubSecurityMaster(new Object[]{KAYNES_SECURITY_MASTER_ID, "KAYNES", "Kaynes Technology India Limited"});
        stubNoAliases();

        CompanyMatch match = resolver.resolve("Kaynes Technology India Limited");

        assertThat(match.matchType()).isEqualTo("EXACT_SECURITY_MASTER");
        assertThat(match.matchedSecurityMasterId()).isEqualTo(KAYNES_SECURITY_MASTER_ID);
        assertThat(match.matchedInstrumentId()).isNull();
    }

    @Test
    void aResolvedSecurityMasterSymbolThatIsAlsoTrackedReturnsTheTrackedInstrumentId() {
        // The instrument's own stored name ("TCS Limited") doesn't exactly match the extracted
        // text, so step 1 (exact instrument match) genuinely fails - but the security_master exact
        // match (step 2) resolves to the SAME real symbol as a tracked instrument, and must be
        // reported as tracked, never as an untracked candidate.
        stubInstruments(new Object[]{TCS_INSTRUMENT_ID, "TCS", "TCS Limited"});
        stubSecurityMaster(new Object[]{UUID.randomUUID(), "TCS", "Tata Consultancy Services Limited"});
        stubNoAliases();

        CompanyMatch match = resolver.resolve("Tata Consultancy Services Limited");

        assertThat(match.matchType()).isEqualTo("EXACT_SECURITY_MASTER");
        assertThat(match.matchedInstrumentId()).isEqualTo(TCS_INSTRUMENT_ID);
        assertThat(match.matchedSecurityMasterId()).isNull();
    }

    @Test
    void aliasPathIsReachedWhenNoExactMatchExists() {
        stubInstruments();
        stubSecurityMaster(new Object[]{BEL_SECURITY_MASTER_ID, "BEL", "Bharat Electronics Limited"});
        stubAlias("BEL");

        CompanyMatch match = resolver.resolve("Defence PSU BEL Corp");

        assertThat(match.matchType()).isEqualTo("ALIAS");
        assertThat(match.matchedSecurityMasterId()).isEqualTo(BEL_SECURITY_MASTER_ID);
    }

    @Test
    void safeSubstringMatchWhenExactlyOneCandidate() {
        stubInstruments();
        stubSecurityMaster(new Object[]{KAYNES_SECURITY_MASTER_ID, "KAYNES", "Kaynes Technology India Limited"});
        stubNoAliases();

        CompanyMatch match = resolver.resolve("Kaynes Technology");

        assertThat(match.matchType()).isEqualTo("SAFE_SUBSTRING");
        assertThat(match.matchedSecurityMasterId()).isEqualTo(KAYNES_SECURITY_MASTER_ID);
    }

    @Test
    void ambiguousSubstringMatchWithMultipleCandidatesIsUnresolvedNeverGuessed() {
        // "Adani" alone plausibly substring-matches BOTH Adani Enterprises and Adani Ports -
        // genuinely ambiguous, must never guess which one the article meant.
        stubInstruments();
        stubSecurityMaster(
            new Object[]{ADANI_ENTERPRISES_ID, "ADANIENT", "Adani Enterprises Limited"},
            new Object[]{ADANI_PORTS_ID, "ADANIPORTS", "Adani Ports and Special Economic Zone Limited"}
        );
        stubNoAliases();

        CompanyMatch match = resolver.resolve("Adani");

        assertThat(match).isEqualTo(CompanyMatch.UNRESOLVED);
        assertThat(match.matchedInstrumentId()).isNull();
        assertThat(match.matchedSecurityMasterId()).isNull();
    }

    @Test
    void noMatchAnywhereIsUnresolved() {
        stubInstruments();
        stubSecurityMaster();
        stubNoAliases();

        CompanyMatch match = resolver.resolve("Some Totally Unknown Private Company");

        assertThat(match).isEqualTo(CompanyMatch.UNRESOLVED);
    }

    @Test
    void blankCompanyNameIsUnresolvedWithoutQueryingAnything() {
        CompanyMatch match = resolver.resolve("   ");

        assertThat(match).isEqualTo(CompanyMatch.UNRESOLVED);
    }
}
