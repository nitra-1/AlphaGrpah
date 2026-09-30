package com.alphagraph.ownership.deals;

import com.alphagraph.ownership.api.BulkDeal;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verifyNoInteractions;

class BulkDealsLoaderTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final BulkDealsLoader loader = new BulkDealsLoader(jdbcTemplate);

    @Test
    void emptyOptionalIsANoOpSinceTheNormalizerAlreadyRoutedItToDiscovery() {
        loader.load(Optional.empty());

        verifyNoInteractions(jdbcTemplate);
    }

    @Test
    void presentDealIsUpsertedIntoBulkDeals() {
        BulkDeal deal = new BulkDeal(
            UUID.randomUUID(), "RELIANCE", LocalDate.of(2026, 7, 28), "SOME FUND", "BUY", 222230L, new BigDecimal("83.00"), "BULK"
        );

        loader.load(Optional.of(deal));

        // Real invocation shape (SQL text + args), not a fragile matcher over the varargs method -
        // mixing a text matcher with a whole-array any() for JdbcTemplate.update(String, Object...)
        // is known-fragile in this codebase (see EconomicEventWriterTest's own history).
        Object[] rawArgs = mockingDetails(jdbcTemplate).getInvocations().stream()
            .findFirst().orElseThrow().getRawArguments();
        assertThat((String) rawArgs[0]).contains("INSERT INTO ownership.bulk_deals");
        Object[] sqlArgs = (Object[]) rawArgs[1];
        assertThat(sqlArgs[0]).isInstanceOf(UUID.class);
        assertThat(java.util.Arrays.copyOfRange(sqlArgs, 1, sqlArgs.length)).containsExactly(
            deal.instrumentId(), deal.dealDate(), deal.clientName(),
            deal.buySell(), deal.quantity(), deal.price(), deal.dealType()
        );
    }
}
