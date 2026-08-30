package com.alphagraph.ownership.deals;

import com.alphagraph.ownership.interpretation.ParticipantResolver;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

import static com.alphagraph.ownership.deals.DiscoveredDealWriter.normalizeClientName;

class DiscoveredDealWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final ParticipantResolver participantResolver = mock(ParticipantResolver.class);
    private final DiscoveredDealWriter writer = new DiscoveredDealWriter(jdbcTemplate, participantResolver);

    @Test
    void validRowWritesBothTheRawDealAndTheDiscoveryStatus() {
        RawDealRow raw = new RawDealRow("BULK", "AASTHA", "Aastha Spintex Limited", "28-JUL-2026", "D3 STOCK VISION LLP", "BUY", "222230", "83.00");

        writer.capture(raw);

        long updateCalls = mockingDetails(jdbcTemplate).getInvocations().stream()
            .map(Invocation::getMethod).filter(method -> method.getName().equals("update")).count();
        assertThat(updateCalls).isEqualTo(2);
    }

    @Test
    void malformedQuantityIsSwallowedNotPropagated() {
        // A capture bug must never suppress or change BulkDealsNormalizer's real "Unknown
        // instrument" rejection - this is the property that matters, not the DB call itself.
        RawDealRow raw = new RawDealRow("BULK", "AASTHA", "Aastha Spintex Limited", "28-JUL-2026", "D3 STOCK VISION LLP", "BUY", "not-a-number", "83.00");

        assertThatCode(() -> writer.capture(raw)).doesNotThrowAnyException();
    }

    @Test
    void malformedDealDateIsSwallowedNotPropagated() {
        RawDealRow raw = new RawDealRow("BULK", "AASTHA", "Aastha Spintex Limited", "not-a-date", "D3 STOCK VISION LLP", "BUY", "222230", "83.00");

        assertThatCode(() -> writer.capture(raw)).doesNotThrowAnyException();
    }

    @Test
    void captureStoresTheNormalizedClientNameAlongsideTheRawOne() {
        RawDealRow raw = new RawDealRow(
            "BULK", "AASTHA", "Aastha Spintex Limited", "28-JUL-2026", "  D3 Stock-Vision, LLP.  ", "BUY", "222230", "83.00"
        );

        writer.capture(raw);

        // Invocation.getArguments() flattens the update(String, Object...) call to [sql, ...varargs] -
        // the 13-column deal insert is the only "update" invocation with 14 total elements.
        Object[] insertArgs = mockingDetails(jdbcTemplate).getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("update"))
            .map(Invocation::getArguments)
            .filter(args -> args.length == 14)
            .findFirst().orElseThrow();
        assertThat(insertArgs[5]).isEqualTo("  D3 Stock-Vision, LLP.  ");
        assertThat(insertArgs[6]).isEqualTo("D3 STOCKVISION LLP");
    }

    @Test
    void normalizeClientNameUppercasesTrimsStripsPunctuationAndCollapsesWhitespace() {
        assertThat(normalizeClientName("  D3 Stock-Vision, LLP.  ")).isEqualTo("D3 STOCKVISION LLP");
        assertThat(normalizeClientName("ABC Mutual Fund")).isEqualTo("ABC MUTUAL FUND");
        assertThat(normalizeClientName("ABC   MUTUAL    FUND")).isEqualTo("ABC MUTUAL FUND");
    }

    @Test
    void normalizeClientNameOfNullIsNull() {
        assertThat(normalizeClientName(null)).isNull();
    }

    @Test
    void captureResolvesAndStoresTheParticipantId() {
        java.util.UUID participantId = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(participantResolver.resolve("D3 Stock-Vision LLP", "D3 STOCKVISION LLP")).thenReturn(participantId);
        RawDealRow raw = new RawDealRow("BULK", "AASTHA", "Aastha Spintex Limited", "28-JUL-2026", "D3 Stock-Vision LLP", "BUY", "222230", "83.00");

        writer.capture(raw);

        Object[] insertArgs = mockingDetails(jdbcTemplate).getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("update"))
            .map(Invocation::getArguments)
            .filter(args -> args.length == 14)
            .findFirst().orElseThrow();
        assertThat(insertArgs[12]).isEqualTo(participantId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aMatchingRowUnderADifferentDealTypeIsMarkedAsADuplicate() {
        java.util.UUID canonicalId = java.util.UUID.randomUUID();
        org.mockito.Mockito.when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
                org.mockito.ArgumentMatchers.any(Object[].class)
            ))
            .thenReturn(java.util.List.of(canonicalId));
        RawDealRow blockRow = new RawDealRow(
            "BLOCK", "LENSKART", "Lenskart Solutions Ltd", "28-AUG-2026", "ALPHA WAVE VENTURES II LP", "SELL", "9838209", "630.00"
        );

        writer.capture(blockRow);

        Object[] insertArgs = mockingDetails(jdbcTemplate).getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("update"))
            .map(Invocation::getArguments)
            .filter(args -> args.length == 14)
            .findFirst().orElseThrow();
        assertThat(insertArgs[13]).isEqualTo(canonicalId);
    }

    @Test
    @SuppressWarnings("unchecked")
    void aGenuinelyDifferentDealIsNotMarkedAsADuplicate() {
        org.mockito.Mockito.when(jdbcTemplate.query(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(org.springframework.jdbc.core.RowMapper.class),
                org.mockito.ArgumentMatchers.any(Object[].class)
            ))
            .thenReturn(java.util.List.of());
        RawDealRow raw = new RawDealRow("BULK", "AASTHA", "Aastha Spintex Limited", "28-JUL-2026", "D3 Stock-Vision LLP", "BUY", "222230", "83.00");

        writer.capture(raw);

        Object[] insertArgs = mockingDetails(jdbcTemplate).getInvocations().stream()
            .filter(inv -> inv.getMethod().getName().equals("update"))
            .map(Invocation::getArguments)
            .filter(args -> args.length == 14)
            .findFirst().orElseThrow();
        assertThat(insertArgs[13]).isNull();
    }

    @Test
    void aParticipantResolutionFailureIsSwallowedAndTheRowStillCaptures() {
        org.mockito.Mockito.when(participantResolver.resolve(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString()))
            .thenThrow(new RuntimeException("boom"));
        RawDealRow raw = new RawDealRow("BULK", "AASTHA", "Aastha Spintex Limited", "28-JUL-2026", "D3 Stock-Vision LLP", "BUY", "222230", "83.00");

        assertThatCode(() -> writer.capture(raw)).doesNotThrowAnyException();

        long updateCalls = mockingDetails(jdbcTemplate).getInvocations().stream()
            .map(Invocation::getMethod).filter(method -> method.getName().equals("update")).count();
        assertThat(updateCalls).isEqualTo(2);
    }
}
