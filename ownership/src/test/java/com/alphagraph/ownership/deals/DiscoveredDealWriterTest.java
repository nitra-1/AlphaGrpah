package com.alphagraph.ownership.deals;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class DiscoveredDealWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DiscoveredDealWriter writer = new DiscoveredDealWriter(jdbcTemplate);

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
}
