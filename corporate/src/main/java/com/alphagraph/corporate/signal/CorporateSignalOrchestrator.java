package com.alphagraph.corporate.signal;

import com.alphagraph.corporate.api.CorporateEvent;
import com.alphagraph.corporate.api.CorporateScore;
import com.alphagraph.corporate.api.EventSignal;
import com.alphagraph.corporate.api.ManagementCommentarySnapshot;
import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.OrderBookSnapshot;
import com.alphagraph.corporate.commentary.ManagementSnapshotReader;
import com.alphagraph.corporate.events.CorporateEventReader;
import com.alphagraph.corporate.news.NewsCatalystSnapshotReader;
import com.alphagraph.corporate.orderbook.OrderBookSnapshotReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Recomputes every instrument with at least one corporate engine's output on record - the driving
 * set is the union of instrument_ids across order_book_snapshots/management_commentary_snapshots/
 * news_catalyst_scores/corporate_events, not "all 8 tracked instruments unconditionally": an
 * instrument with zero corporate signal history anywhere would only ever produce a meaningless
 * neutral score, noise rather than signal, so it's simply not computed rather than fabricated.
 *
 * <p>Events considered use a 90-day lookback (unlike the Score-implementing domains, which each
 * read a single latest snapshot) - corporate_events is append-only with no as_of_date, so "recent"
 * has to be defined explicitly rather than inherited from a snapshot table's own cadence.
 */
@Component
public class CorporateSignalOrchestrator {

    private static final Logger log = LoggerFactory.getLogger(CorporateSignalOrchestrator.class);
    private static final int EVENT_LOOKBACK_DAYS = 90;

    private final JdbcTemplate jdbcTemplate;
    private final OrderBookSnapshotReader orderBookSnapshotReader;
    private final ManagementSnapshotReader managementSnapshotReader;
    private final NewsCatalystSnapshotReader newsCatalystSnapshotReader;
    private final CorporateEventReader corporateEventReader;
    private final CorporateSignalRuleSetLoader ruleSetLoader;
    private final CorporateSignalEngine engine;
    private final CorporateScoreStore scoreStore;

    public CorporateSignalOrchestrator(
        JdbcTemplate jdbcTemplate, OrderBookSnapshotReader orderBookSnapshotReader,
        ManagementSnapshotReader managementSnapshotReader, NewsCatalystSnapshotReader newsCatalystSnapshotReader,
        CorporateEventReader corporateEventReader, CorporateSignalRuleSetLoader ruleSetLoader,
        CorporateSignalEngine engine, CorporateScoreStore scoreStore
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.orderBookSnapshotReader = orderBookSnapshotReader;
        this.managementSnapshotReader = managementSnapshotReader;
        this.newsCatalystSnapshotReader = newsCatalystSnapshotReader;
        this.corporateEventReader = corporateEventReader;
        this.ruleSetLoader = ruleSetLoader;
        this.engine = engine;
        this.scoreStore = scoreStore;
    }

    public void recomputeAllInstruments() {
        List<UUID> instrumentIds = findDrivingSet();
        int updated = 0;
        for (UUID instrumentId : instrumentIds) {
            try {
                recomputeOne(instrumentId);
                updated++;
            } catch (Exception e) {
                log.error("Failed to recompute corporate score for instrument {}: {}", instrumentId, e.getMessage(), e);
            }
        }
        log.info("Corporate Signal update: {} instrument scores recomputed (of {} candidates)", updated, instrumentIds.size());
    }

    private List<UUID> findDrivingSet() {
        return jdbcTemplate.query(
            """
            SELECT DISTINCT instrument_id FROM (
                SELECT instrument_id FROM corporate.order_book_snapshots
                UNION SELECT instrument_id FROM corporate.management_commentary_snapshots
                UNION SELECT instrument_id FROM corporate.news_catalyst_scores
                UNION SELECT instrument_id FROM corporate.corporate_events
            ) driving_set
            """,
            (rs, rowNum) -> (UUID) rs.getObject("instrument_id")
        );
    }

    private void recomputeOne(UUID instrumentId) {
        Optional<OrderBookSnapshot> orderBook = orderBookSnapshotReader.findLatest(instrumentId);
        Optional<ManagementCommentarySnapshot> management = managementSnapshotReader.findLatest(instrumentId);
        Optional<NewsCatalystSnapshot> news = newsCatalystSnapshotReader.findLatest(instrumentId);
        List<CorporateEvent> recentEvents = corporateEventReader.findRecentByInstrument(instrumentId, EVENT_LOOKBACK_DAYS);

        String symbol = resolveSymbol(orderBook, management, news, recentEvents, instrumentId);
        int netEventSignal = netEventSignal(recentEvents);
        LocalDate asOfDate = LocalDate.now();

        CorporateSignalInput input = new CorporateSignalInput(
            instrumentId, symbol,
            orderBook.map(OrderBookSnapshot::qualityScore).orElse(null),
            management.map(ManagementCommentarySnapshot::growthVisibilityScore).orElse(null),
            news.map(NewsCatalystSnapshot::catalystScore).orElse(null),
            netEventSignal, recentEvents.size(), asOfDate
        );

        CorporateScore score = engine.calculate(input, ruleSetLoader.loadActiveRules());
        scoreStore.write(score);
    }

    private static int netEventSignal(List<CorporateEvent> events) {
        int net = 0;
        for (CorporateEvent event : events) {
            if (event.signal() == EventSignal.POSITIVE) {
                net++;
            } else if (event.signal() == EventSignal.NEGATIVE) {
                net--;
            }
        }
        return net;
    }

    private String resolveSymbol(
        Optional<OrderBookSnapshot> orderBook, Optional<ManagementCommentarySnapshot> management,
        Optional<NewsCatalystSnapshot> news, List<CorporateEvent> recentEvents, UUID instrumentId
    ) {
        if (orderBook.isPresent()) {
            return orderBook.get().symbol();
        }
        if (management.isPresent()) {
            return management.get().symbol();
        }
        if (news.isPresent()) {
            return news.get().symbol();
        }
        if (!recentEvents.isEmpty()) {
            return recentEvents.get(0).symbol();
        }
        return jdbcTemplate.queryForObject("SELECT symbol FROM reference.instruments WHERE id = ?", String.class, instrumentId);
    }
}
