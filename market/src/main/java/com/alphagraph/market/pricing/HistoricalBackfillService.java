package com.alphagraph.market.pricing;

import com.alphagraph.market.api.DailyPrice;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Productionizes the manual backfill process from docs/006_Universe_Expansion_Runbook.md into a
 * real service: when a new instrument is added via the "Add Instrument" admin form, it starts
 * with zero price history (only {@code HttpBhavdataCollector}, docker/prod only, auto-scales
 * going forward day-by-day - nothing back-fills the past for a brand-new symbol). Runs
 * {@code @Async} so the form's HTTP request returns immediately rather than blocking on ~80
 * sequential NSE fetches.
 *
 * <p>Fetches the last {@code BACKFILL_CALENDAR_DAYS} calendar days ending yesterday (today's
 * bhavcopy may not be published yet) - a dynamic, always-current window, unlike batch 1's
 * deliberately fixed historical window (which existed only to align the original 20 instruments
 * on one shared range). Symbol matching splits each full row on the real CSV column order and
 * compares field 0 for an exact match (same approach as {@link BhavdataParser}/
 * {@code SecurityMasterParser}) rather than a substring/capture-group slice - deliberately not
 * the regex-capture-then-reindex approach an earlier draft of this class used, which shifted
 * every subsequent field index by 2 and was caught before ever running against live data.
 * Reuses the runbook's other disclosed gotcha: NSE re-serves the prior real trading day's file
 * for a market holiday instead of 404ing, so per-date matches are deduplicated by trade date
 * before loading, not just left to {@code ON CONFLICT DO NOTHING} to quietly absorb.
 */
@Service
public class HistoricalBackfillService {

    private static final Logger log = LoggerFactory.getLogger(HistoricalBackfillService.class);
    private static final int BACKFILL_CALENDAR_DAYS = 120;
    private static final DateTimeFormatter URL_DATE_FORMAT = DateTimeFormatter.ofPattern("ddMMyyyy");
    private static final DateTimeFormatter ROW_DATE_FORMAT = new DateTimeFormatterBuilder()
        .parseCaseInsensitive().appendPattern("dd-MMM-yyyy").toFormatter(Locale.ENGLISH);

    private final RestClient restClient;
    private final String urlTemplate;
    private final Clock clock;
    private final DailyPriceLoader loader;

    @Autowired
    public HistoricalBackfillService(
        RestClient.Builder restClientBuilder,
        @Value("${alphagraph.market.nse-bhavdata-url-template:https://archives.nseindia.com/products/content/sec_bhavdata_full_%s.csv}")
        String urlTemplate,
        DailyPriceLoader loader
    ) {
        this(restClientBuilder, urlTemplate, Clock.system(ZoneId.of("Asia/Kolkata")), loader);
    }

    /** Package-private: lets tests inject a fixed Clock instead of depending on the real date. */
    HistoricalBackfillService(RestClient.Builder restClientBuilder, String urlTemplate, Clock clock, DailyPriceLoader loader) {
        this.restClient = restClientBuilder.defaultHeader(HttpHeaders.USER_AGENT, "Mozilla/5.0 (compatible; AlphaGraph/1.0)").build();
        this.urlTemplate = urlTemplate;
        this.clock = clock;
        this.loader = loader;
    }

    @Async
    public void backfillAsync(UUID instrumentId, String symbol) {
        Map<LocalDate, DailyPrice> byDate = new LinkedHashMap<>();

        LocalDate date = LocalDate.now(clock).minusDays(1);
        LocalDate earliest = date.minusDays(BACKFILL_CALENDAR_DAYS);
        int fetched = 0;
        int failed = 0;

        while (!date.isBefore(earliest)) {
            if (date.getDayOfWeek() != DayOfWeek.SATURDAY && date.getDayOfWeek() != DayOfWeek.SUNDAY) {
                try {
                    fetchOneDay(instrumentId, symbol, date, byDate);
                    fetched++;
                } catch (Exception e) {
                    failed++;
                    log.warn("Backfill: skipping {} for {} - {}", date, symbol, e.getMessage());
                }
            }
            date = date.minusDays(1);
        }

        byDate.values().forEach(loader::load);
        log.info(
            "Backfill complete for {}: {} distinct trading days loaded ({} URLs fetched, {} failed/skipped)",
            symbol, byDate.size(), fetched, failed
        );
    }

    /**
     * Full-row column order (matches BhavdataParser/SecurityMasterParser's proven approach -
     * every field kept at its real CSV index, no capture-group slicing that would shift indices
     * and risk exactly the kind of off-by-N bug a slice-then-reindex approach invites): SYMBOL(0),
     * SERIES(1), DATE1(2), PREV_CLOSE(3), OPEN_PRICE(4), HIGH_PRICE(5), LOW_PRICE(6),
     * LAST_PRICE(7), CLOSE_PRICE(8), AVG_PRICE(9), TTL_TRD_QNTY(10), TURNOVER_LACS(11),
     * NO_OF_TRADES(12), DELIV_QTY(13), DELIV_PER(14).
     */
    private void fetchOneDay(UUID instrumentId, String symbol, LocalDate date, Map<LocalDate, DailyPrice> byDate) {
        String url = urlTemplate.formatted(date.format(URL_DATE_FORMAT));
        String body;
        try {
            body = restClient.get().uri(url).retrieve().body(String.class);
        } catch (HttpClientErrorException.NotFound e) {
            return;
        }
        if (body == null || body.isBlank()) {
            return;
        }

        body.lines()
            .map(line -> line.split(",\\s*", -1))
            .filter(fields -> fields.length >= 15 && "EQ".equals(fields[1].trim()) && symbol.equals(fields[0].trim()))
            .findFirst()
            .ifPresent(fields -> {
                LocalDate tradeDate = LocalDate.parse(fields[2].trim(), ROW_DATE_FORMAT);
                BigDecimal open = new BigDecimal(fields[4].trim());
                BigDecimal high = new BigDecimal(fields[5].trim());
                BigDecimal low = new BigDecimal(fields[6].trim());
                BigDecimal close = new BigDecimal(fields[8].trim());
                long volume = Long.parseLong(fields[10].trim());
                String deliv = fields[14].trim();
                BigDecimal deliveryPercentage = deliv.isEmpty() ? null : new BigDecimal(deliv);

                byDate.put(tradeDate, new DailyPrice(instrumentId, symbol, tradeDate, open, high, low, close, volume, deliveryPercentage));
            });
    }
}
