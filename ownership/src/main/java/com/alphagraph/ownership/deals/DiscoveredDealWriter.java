package com.alphagraph.ownership.deals;

import com.alphagraph.ownership.interpretation.ParticipantResolver;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Locale;
import java.util.UUID;

/**
 * Persists a bulk/block deal whose symbol didn't resolve to a tracked instrument, instead of
 * letting it vanish with the "Unknown instrument" rejection that quarantines it from
 * {@code ownership.bulk_deals}. Writes to two tables: the raw, append-only
 * {@code ownership.discovered_deals} log, and {@code ownership.discovery_status} (upserted to
 * {@code NEW} on first sight, or just has {@code last_detected_at} bumped on a repeat sighting -
 * a symbol already marked {@code DISMISSED} stays dismissed even if new deal activity arrives).
 *
 * <p>Called from {@link BulkDealsNormalizer} right before it throws. Deliberately best-effort:
 * any failure here (a malformed quantity/price, a transient DB issue) is caught and logged, never
 * propagated - a bug in this side-channel capture must never suppress or change the real
 * "Unknown instrument" rejection the caller is about to throw.
 *
 * <p>Sprint 3: also resolves the deal's participant (see {@link ParticipantResolver}) so
 * repetition/breadth/churn can be judged per genuine real-world entity, not per raw string.
 * Resolution failure is swallowed the same best-effort way - a row still gets captured with a null
 * {@code participant_id} rather than losing the deal entirely; {@code
 * InstitutionalInterpretationOrchestrator}'s self-healing pass picks up anything still unresolved.
 */
@Component
class DiscoveredDealWriter {

    private static final Logger log = LoggerFactory.getLogger(DiscoveredDealWriter.class);

    private final JdbcTemplate jdbcTemplate;
    private final ParticipantResolver participantResolver;

    DiscoveredDealWriter(JdbcTemplate jdbcTemplate, ParticipantResolver participantResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.participantResolver = participantResolver;
    }

    void capture(RawDealRow raw) {
        try {
            LocalDate dealDate = LocalDate.parse(raw.dealDate(), BulkDealsNormalizer.DATE_FORMAT);
            long quantity = Long.parseLong(raw.quantity());
            BigDecimal price = new BigDecimal(raw.price());
            BigDecimal dealValue = price.multiply(BigDecimal.valueOf(quantity));
            String normalizedName = normalizeClientName(raw.clientName());
            UUID participantId = resolveParticipant(raw.clientName(), normalizedName);

            jdbcTemplate.update(
                """
                INSERT INTO ownership.discovered_deals
                    (id, symbol, security_name, deal_date, client_name, client_name_normalized, buy_sell, quantity, price, deal_value, deal_type, participant_id)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (symbol, deal_date, client_name, buy_sell, deal_type) DO NOTHING
                """,
                UUID.randomUUID(), raw.symbol(), blankToNull(raw.securityName()), dealDate, raw.clientName(),
                normalizedName, raw.buySell(), quantity, price, dealValue, raw.dealType(), participantId
            );

            jdbcTemplate.update(
                """
                INSERT INTO ownership.discovery_status (symbol, first_detected_at, last_detected_at)
                VALUES (?, now(), now())
                ON CONFLICT (symbol) DO UPDATE SET last_detected_at = now()
                """,
                raw.symbol()
            );
        } catch (Exception e) {
            log.warn("Failed to capture discovery candidate for symbol {}: {}", raw.symbol(), e.getMessage());
        }
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    /** Best-effort on its own - a participant-resolution failure must never block capturing the rest of the row. */
    private UUID resolveParticipant(String rawName, String normalizedName) {
        if (normalizedName == null) {
            return null;
        }
        try {
            return participantResolver.resolve(rawName, normalizedName);
        } catch (Exception e) {
            log.warn("Failed to resolve participant for {}: {}", rawName, e.getMessage());
            return null;
        }
    }

    /**
     * Deterministic normalization only (uppercase, trim, strip punctuation, collapse whitespace) -
     * not real entity resolution; see V5's migration comment for the identical SQL-side algorithm
     * used to backfill rows captured before this column existed. "ABC MUTUAL FUND LTD" vs
     * "ABC MUTUAL FUND" won't collapse under this - a known, accepted v1 limitation.
     */
    static String normalizeClientName(String clientName) {
        if (clientName == null) {
            return null;
        }
        String upperTrimmed = clientName.trim().toUpperCase(Locale.ROOT);
        String noPunctuation = upperTrimmed.replaceAll("[^A-Z0-9 ]", "");
        String collapsed = noPunctuation.replaceAll("\\s+", " ").trim();
        return collapsed.isEmpty() ? null : collapsed;
    }
}
