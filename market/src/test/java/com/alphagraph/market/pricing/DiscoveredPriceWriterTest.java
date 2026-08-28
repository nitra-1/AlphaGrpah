package com.alphagraph.market.pricing;

import org.junit.jupiter.api.Test;
import org.mockito.invocation.Invocation;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;

class DiscoveredPriceWriterTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final DiscoveredPriceWriter writer = new DiscoveredPriceWriter(jdbcTemplate);

    @Test
    void validRowWritesOneRow() {
        RawDeliveryRow raw = new RawDeliveryRow(
            "AASTHA", "EQ", "24-Jul-2026", "10", "11", "9", "10.5", "1000", "5.0", "56.78"
        );

        writer.capture(raw);

        long updateCalls = mockingDetails(jdbcTemplate).getInvocations().stream()
            .map(Invocation::getMethod).filter(method -> method.getName().equals("update")).count();
        assertThat(updateCalls).isEqualTo(1);
    }

    @Test
    void malformedVolumeIsSwallowedNotPropagated() {
        RawDeliveryRow raw = new RawDeliveryRow(
            "AASTHA", "EQ", "24-Jul-2026", "10", "11", "9", "10.5", "not-a-number", "5.0", "56.78"
        );

        assertThatCode(() -> writer.capture(raw)).doesNotThrowAnyException();
    }

    @Test
    void malformedTradeDateIsSwallowedNotPropagated() {
        RawDeliveryRow raw = new RawDeliveryRow(
            "AASTHA", "EQ", "not-a-date", "10", "11", "9", "10.5", "1000", "5.0", "56.78"
        );

        assertThatCode(() -> writer.capture(raw)).doesNotThrowAnyException();
    }

    @Test
    void blankTurnoverBecomesNullRatherThanFailing() {
        RawDeliveryRow raw = new RawDeliveryRow(
            "AASTHA", "EQ", "24-Jul-2026", "10", "11", "9", "10.5", "1000", "", "56.78"
        );

        assertThatCode(() -> writer.capture(raw)).doesNotThrowAnyException();
    }
}
