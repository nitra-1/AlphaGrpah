package com.alphagraph.corporate.news;

import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.reference.instrument.SectorService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Writes one document's {@link ParsedNewsDocument} into {@code corporate.economic_events}/
 * {@code _sector_impacts}/{@code _company_exposures}, resolving each sector via {@link
 * SectorService#findOrCreateByName} and each company via {@link CompanyResolver}. A document
 * whose {@code event} is null (no event-level group was ever extracted - shouldn't happen for a
 * real extraction) writes nothing. {@code expiry_date} is a simple rule-driven fixed cutoff per
 * {@code magnitude} (half-life v1, disclosed simplification - see {@link
 * EconomicEventRuleSetLoader}), computed from the document's own {@code announcedAt}, never
 * "now" - a backfilled older article must not appear to expire later than it should.
 *
 * <p>An {@code UNRESOLVED} company exposure is still written (raw name visible on the exposure
 * row, for detail-view auditability) but **never** creates or updates a {@code
 * news_discovery_candidates} row - only a real, resolved-but-untracked company (i.e.
 * {@code matchedInstrumentId} is null and {@code matchedSecurityMasterId} is not) becomes a
 * candidate. A resolved-and-already-tracked company writes its exposure with {@code
 * matched_instrument_id} set and is never added to the candidate table either - it's already
 * fully tracked, nothing to discover. Updating an existing candidate never overwrites its own
 * {@code status} if an analyst has already moved it past {@code NEW} - a later article must never
 * silently un-dismiss or reset an analyst's own decision.
 */
@Component
class EconomicEventWriter {

    private final JdbcTemplate jdbcTemplate;
    private final SectorService sectorService;
    private final CompanyResolver companyResolver;

    EconomicEventWriter(JdbcTemplate jdbcTemplate, SectorService sectorService, CompanyResolver companyResolver) {
        this.jdbcTemplate = jdbcTemplate;
        this.sectorService = sectorService;
        this.companyResolver = companyResolver;
    }

    void write(Instant announcedAt, ParsedNewsDocument document, RuleSet rules) {
        if (document.event() == null) {
            return;
        }
        ParsedEconomicEvent event = document.event();
        LocalDate announcedDate = announcedAt.atZone(ZoneOffset.UTC).toLocalDate();
        LocalDate expiryDate = announcedDate.plusDays(expiryDaysFor(event.magnitude(), rules));
        String clusterKey = clusterKeyFor(event.theme(), announcedDate);

        UUID eventId = UUID.randomUUID();
        jdbcTemplate.update(
            """
            INSERT INTO corporate.economic_events
                (id, document_id, theme, economic_relevance, direction, magnitude, confidence, horizon, cluster_key, expiry_date)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            eventId, document.documentId(), nullToEmpty(event.theme()), event.economicRelevance(),
            event.direction(), event.magnitude(), event.confidence(), event.horizon(), clusterKey, Date.valueOf(expiryDate)
        );

        for (ParsedSectorImpact sectorImpact : document.sectorImpacts()) {
            writeSectorImpact(eventId, sectorImpact);
        }
        for (ParsedCompanyImpact companyImpact : document.companyImpacts()) {
            writeCompanyExposure(eventId, companyImpact);
        }
    }

    private void writeSectorImpact(UUID eventId, ParsedSectorImpact impact) {
        UUID sectorId = sectorService.findOrCreateByName(impact.sector().trim());
        jdbcTemplate.update(
            """
            INSERT INTO corporate.economic_event_sector_impacts
                (id, event_id, sector_id, sector_name_raw, direction, strength, confidence, mechanism)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), eventId, sectorId, impact.sector(), impact.direction(), impact.strength(),
            impact.confidence(), nullToNull(impact.mechanism())
        );
    }

    private void writeCompanyExposure(UUID eventId, ParsedCompanyImpact impact) {
        CompanyMatch match = companyResolver.resolve(impact.companyName());

        jdbcTemplate.update(
            """
            INSERT INTO corporate.economic_event_company_exposures
                (id, event_id, company_name_raw, matched_instrument_id, matched_security_master_id,
                 match_type, exposure_type, direction, impact_strength, confidence, reason)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """,
            UUID.randomUUID(), eventId, impact.companyName(), match.matchedInstrumentId(), match.matchedSecurityMasterId(),
            match.matchType(), impact.exposureType(), impact.direction(), impact.impactStrength(),
            impact.confidence(), nullToNull(impact.impactSummary())
        );

        boolean isNewDiscoveryCandidate = match.matchedInstrumentId() == null && match.matchedSecurityMasterId() != null;
        if (isNewDiscoveryCandidate) {
            upsertDiscoveryCandidate(match, impact);
        }
    }

    private void upsertDiscoveryCandidate(CompanyMatch match, ParsedCompanyImpact impact) {
        jdbcTemplate.update(
            """
            INSERT INTO corporate.news_discovery_candidates
                (symbol, company_name, security_master_id, first_seen_at, last_seen_at, exposure_count, best_direction, best_exposure_type, status)
            VALUES (?, ?, ?, now(), now(), 1, ?, ?, 'NEW')
            ON CONFLICT (symbol) DO UPDATE SET
                last_seen_at = now(),
                exposure_count = corporate.news_discovery_candidates.exposure_count + 1,
                best_direction = EXCLUDED.best_direction,
                best_exposure_type = EXCLUDED.best_exposure_type
            """,
            match.symbol(), match.companyName(), match.matchedSecurityMasterId(), impact.direction(), impact.exposureType()
        );
    }

    private static int expiryDaysFor(String magnitude, RuleSet rules) {
        String ruleName = switch (magnitude == null ? "" : magnitude) {
            case "HIGH" -> "news-expiry-days-high";
            case "MEDIUM" -> "news-expiry-days-medium";
            default -> "news-expiry-days-low";
        };
        int defaultValue = switch (magnitude == null ? "" : magnitude) {
            case "HIGH" -> 90;
            case "MEDIUM" -> 30;
            default -> 14;
        };
        return EconomicEventRuleSetLoader.ruleThreshold(rules, ruleName, defaultValue);
    }

    private static String clusterKeyFor(String theme, LocalDate announcedDate) {
        String normalizedTheme = theme == null ? "" : theme.trim().toUpperCase();
        return normalizedTheme + "|" + announcedDate;
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private static String nullToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }
}
