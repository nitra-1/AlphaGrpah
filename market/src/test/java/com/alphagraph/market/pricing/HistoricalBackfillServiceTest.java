package com.alphagraph.market.pricing;

import com.alphagraph.market.api.DailyPrice;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class HistoricalBackfillServiceTest {

    // Fixed to a Friday (Asia/Kolkata) so the expected URL for "yesterday" is deterministic.
    private static final Clock FIXED_CLOCK = Clock.fixed(
        Instant.parse("2026-07-24T12:00:00Z"), ZoneId.of("Asia/Kolkata")
    );
    private static final String URL_TEMPLATE = "https://example-nse-mirror.test/sec_bhavdata_full_%s.csv";
    private static final String BHAVDATA_HEADER =
        "SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER";
    private static final String ACE_ROW_24JUL =
        "ACE, EQ, 24-Jul-2026, 979.00, 975.20, 1051.00, 971.60, 1040.90, 1039.40, 1027.67, 2105588, 21638.42, 80353, 475167, 22.57";
    private static final String GS_ROW =
        "1018GS2026, GS, 24-Jul-2026, 104.29, 104.00, 104.25, 104.00, 104.25, 104.25, 104.02, 666, 0.69, 6, 666, 100.00";

    private final DailyPriceLoader loader = mock(DailyPriceLoader.class);

    @Test
    void fetchEquityRowsKeepsOnlyEqSeriesAndDropsTheHeaderAndOtherSeries() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = String.join("\n", BHAVDATA_HEADER, ACE_ROW_24JUL, GS_ROW);
        server.expect(requestTo("https://example-nse-mirror.test/sec_bhavdata_full_23072026.csv"))
            .andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
        HistoricalBackfillService service = new HistoricalBackfillService(builder, URL_TEMPLATE, FIXED_CLOCK, loader);

        List<String[]> rows = service.fetchEquityRows(LocalDate.of(2026, 7, 23));

        assertThat(rows).hasSize(1);
        assertThat(rows.get(0)[0].trim()).isEqualTo("ACE");
        server.verify();
    }

    @Test
    void fetchEquityRowsReturnsEmptyOn404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example-nse-mirror.test/sec_bhavdata_full_23072026.csv"))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));
        HistoricalBackfillService service = new HistoricalBackfillService(builder, URL_TEMPLATE, FIXED_CLOCK, loader);

        assertThat(service.fetchEquityRows(LocalDate.of(2026, 7, 23))).isEmpty();
    }

    @Test
    void fetchEquityRowsReturnsEmptyOnBlankBody() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://example-nse-mirror.test/sec_bhavdata_full_23072026.csv"))
            .andRespond(withSuccess("", MediaType.TEXT_PLAIN));
        HistoricalBackfillService service = new HistoricalBackfillService(builder, URL_TEMPLATE, FIXED_CLOCK, loader);

        assertThat(service.fetchEquityRows(LocalDate.of(2026, 7, 23))).isEmpty();
    }

    @Test
    void backfillAsyncNeverThrowsWhenEveryDayInTheWindowIs404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(ExpectedCount.manyTimes(), requestTo(startsWith("https://example-nse-mirror.test/sec_bhavdata_full_")))
            .andRespond(withStatus(HttpStatus.NOT_FOUND));
        HistoricalBackfillService service = new HistoricalBackfillService(builder, URL_TEMPLATE, FIXED_CLOCK, loader);

        service.backfillAsync(UUID.randomUUID(), "JUSTIPOED");

        verifyNoInteractions(loader);
    }

    /**
     * The runbook's disclosed NSE quirk: a market holiday re-serves the prior real trading day's
     * file instead of 404ing. Simulated here by returning the *same* 24-Jul-2026 row for every
     * date requested across the whole backfill window - the map keyed by the row's own parsed
     * trade date (not the requested date) must still collapse this to exactly one distinct
     * trading day loaded, not ~85 duplicate loads.
     */
    @Test
    void repeatedIdenticalFileAcrossTheWholeWindowCollapsesToOneDistinctTradingDay() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        String body = String.join("\n", BHAVDATA_HEADER, ACE_ROW_24JUL);
        server.expect(ExpectedCount.manyTimes(), requestTo(startsWith("https://example-nse-mirror.test/sec_bhavdata_full_")))
            .andRespond(withSuccess(body, MediaType.TEXT_PLAIN));
        HistoricalBackfillService service = new HistoricalBackfillService(builder, URL_TEMPLATE, FIXED_CLOCK, loader);
        UUID instrumentId = UUID.randomUUID();

        service.backfillAsync(instrumentId, "ACE");

        verify(loader).load(new DailyPrice(
            instrumentId, "ACE", LocalDate.of(2026, 7, 24),
            new BigDecimal("975.20"), new BigDecimal("1051.00"), new BigDecimal("971.60"), new BigDecimal("1039.40"),
            2105588L, new BigDecimal("22.57")
        ));
        assertThat(mockingDetails(loader).getInvocations()).hasSize(1);
    }
}
