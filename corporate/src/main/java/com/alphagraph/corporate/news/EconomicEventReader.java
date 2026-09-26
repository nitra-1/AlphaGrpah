package com.alphagraph.corporate.news;

import com.alphagraph.corporate.api.CompanyExposureDetail;
import com.alphagraph.corporate.api.EconomicEventDetail;
import com.alphagraph.corporate.api.EconomicEventSummary;
import com.alphagraph.corporate.api.NewsDiscoveryCandidate;
import com.alphagraph.corporate.api.NewsDiscoverySummary;
import com.alphagraph.corporate.api.NewsSourceArticle;
import com.alphagraph.corporate.api.SectorImpactDetail;
import com.alphagraph.corporate.api.SectorImpactMapEntry;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Read side of the News & Economic Discovery module - every method computes its result at read
 * time over {@code corporate.economic_events}/{@code _sector_impacts}/{@code _company_exposures}/
 * {@code news_discovery_candidates}, same "no separately maintained aggregate table" convention as
 * {@code ownership.deals.DiscoveryReader}/{@code api.admin.CronMonitoringRepository}.
 * Cross-schema reads of {@code reference.instruments}/{@code reference.security_master} are by
 * value only (no cross-module Java dependency), same established pattern as
 * {@code ownership.pattern.OwnershipInstrumentLookup}.
 */
@Component
public class EconomicEventReader {

    private final JdbcTemplate jdbcTemplate;

    public EconomicEventReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * "Today" is a rolling trailing-24h window, not calendar-day. {@code totalProcessed} counts
     * every NEWS document that made it past collection dedup (any status but the raw {@code
     * PENDING} row a same-run duplicate title never reaches); {@code economyRelevantCount} is the
     * LLM's own {@code economic_relevance} classification (not the cheap pre-filter's {@code
     * NOT_ECONOMIC} status), since that pre-filter is deliberately inclusion-biased and isn't the
     * authoritative call.
     */
    public NewsDiscoverySummary summary() {
        int totalProcessed = countDocuments("now() - interval '24 hours'");
        int totalProcessedPreviousDay = countDocuments("now() - interval '48 hours'", "now() - interval '24 hours'");

        int economyRelevant = countRelevantEvents("now() - interval '24 hours'");
        int economyRelevantPreviousDay = countRelevantEvents("now() - interval '48 hours'", "now() - interval '24 hours'");

        int uniqueEvents = countDistinctClusters("now() - interval '24 hours'");
        int uniqueEventsPreviousDay = countDistinctClusters("now() - interval '48 hours'", "now() - interval '24 hours'");

        int sectorsImpacted = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT sectorKey) FROM (
                SELECT COALESCE(si.sector_id::text, si.sector_name_raw) AS sectorKey
                FROM corporate.economic_event_sector_impacts si
                JOIN corporate.economic_events e ON e.id = si.event_id
                WHERE e.computed_at >= now() - interval '24 hours'
            ) s
            """,
            Integer.class
        );
        int sectorsImpactedPreviousDay = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT sectorKey) FROM (
                SELECT COALESCE(si.sector_id::text, si.sector_name_raw) AS sectorKey
                FROM corporate.economic_event_sector_impacts si
                JOIN corporate.economic_events e ON e.id = si.event_id
                WHERE e.computed_at >= now() - interval '48 hours' AND e.computed_at < now() - interval '24 hours'
            ) s
            """,
            Integer.class
        );

        int companiesTracked = countCompaniesIdentified("now() - interval '24 hours'", true);
        int companiesUntracked = countCompaniesIdentified("now() - interval '24 hours'", false);
        int companiesPreviousDay = jdbcTemplate.queryForObject(
            """
            SELECT COUNT(DISTINCT ce.company_name_raw) FROM corporate.economic_event_company_exposures ce
            JOIN corporate.economic_events e ON e.id = ce.event_id
            WHERE e.computed_at >= now() - interval '48 hours' AND e.computed_at < now() - interval '24 hours'
              AND ce.match_type <> 'UNRESOLVED'
            """,
            Integer.class
        );

        return new NewsDiscoverySummary(
            totalProcessed, totalProcessedPreviousDay,
            economyRelevant, economyRelevantPreviousDay,
            uniqueEvents, uniqueEventsPreviousDay,
            sectorsImpacted, sectorsImpactedPreviousDay,
            companiesTracked, companiesUntracked, companiesPreviousDay
        );
    }

    private int countDocuments(String lowerBoundExpr) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM corporate.documents WHERE source = 'NEWS' AND status <> 'PENDING' AND announced_at >= " + lowerBoundExpr,
            Integer.class
        );
    }

    private int countDocuments(String lowerBoundExpr, String upperBoundExpr) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM corporate.documents WHERE source = 'NEWS' AND status <> 'PENDING' " +
            "AND announced_at >= " + lowerBoundExpr + " AND announced_at < " + upperBoundExpr,
            Integer.class
        );
    }

    private int countRelevantEvents(String lowerBoundExpr) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT document_id) FROM corporate.economic_events " +
            "WHERE economic_relevance NOT IN ('NON_ECONOMIC', 'LOW_CONFIDENCE') AND computed_at >= " + lowerBoundExpr,
            Integer.class
        );
    }

    private int countRelevantEvents(String lowerBoundExpr, String upperBoundExpr) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT document_id) FROM corporate.economic_events " +
            "WHERE economic_relevance NOT IN ('NON_ECONOMIC', 'LOW_CONFIDENCE') " +
            "AND computed_at >= " + lowerBoundExpr + " AND computed_at < " + upperBoundExpr,
            Integer.class
        );
    }

    private int countDistinctClusters(String lowerBoundExpr) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT cluster_key) FROM corporate.economic_events WHERE computed_at >= " + lowerBoundExpr,
            Integer.class
        );
    }

    private int countDistinctClusters(String lowerBoundExpr, String upperBoundExpr) {
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT cluster_key) FROM corporate.economic_events " +
            "WHERE computed_at >= " + lowerBoundExpr + " AND computed_at < " + upperBoundExpr,
            Integer.class
        );
    }

    private int countCompaniesIdentified(String lowerBoundExpr, boolean tracked) {
        String matchTypeFilter = tracked ? "ce.match_type = 'EXACT_INSTRUMENT'" : "ce.match_type IN ('EXACT_SECURITY_MASTER', 'ALIAS', 'SAFE_SUBSTRING')";
        return jdbcTemplate.queryForObject(
            "SELECT COUNT(DISTINCT ce.company_name_raw) FROM corporate.economic_event_company_exposures ce " +
            "JOIN corporate.economic_events e ON e.id = ce.event_id " +
            "WHERE " + matchTypeFilter + " AND e.computed_at >= " + lowerBoundExpr,
            Integer.class
        );
    }

    /** Latest events first, each paired with a live count of how many other events share its {@code cluster_key}. */
    public List<EconomicEventSummary> findLatestEvents(int limit) {
        return jdbcTemplate.query(
            """
            SELECT e.id, e.theme, e.economic_relevance, e.direction, e.magnitude, e.confidence, e.horizon,
                   e.computed_at, d.announced_at,
                   (SELECT COUNT(*) FROM corporate.economic_events e2 WHERE e2.cluster_key = e.cluster_key) AS source_article_count
            FROM corporate.economic_events e
            JOIN corporate.documents d ON d.id = e.document_id
            ORDER BY e.computed_at DESC
            LIMIT ?
            """,
            (rs, rowNum) -> new EconomicEventSummary(
                (UUID) rs.getObject("id"), rs.getString("theme"), rs.getString("economic_relevance"),
                rs.getString("direction"), rs.getString("magnitude"), rs.getDouble("confidence"), rs.getString("horizon"),
                rs.getInt("source_article_count"), rs.getTimestamp("announced_at").toInstant(), rs.getTimestamp("computed_at").toInstant()
            ),
            limit
        );
    }

    public Optional<EconomicEventDetail> findEventDetail(UUID eventId) {
        List<EconomicEventDetail> rows = jdbcTemplate.query(
            "SELECT id, theme, economic_relevance, direction, magnitude, confidence, horizon, computed_at, cluster_key " +
            "FROM corporate.economic_events WHERE id = ?",
            (rs, rowNum) -> new EconomicEventDetail(
                (UUID) rs.getObject("id"), rs.getString("theme"), rs.getString("economic_relevance"),
                rs.getString("direction"), rs.getString("magnitude"), rs.getDouble("confidence"), rs.getString("horizon"),
                rs.getTimestamp("computed_at").toInstant(),
                findSectorImpacts(eventId), findCompanyExposures(eventId), findSourceArticles(rs.getString("cluster_key"))
            ),
            eventId
        );
        return rows.stream().findFirst();
    }

    private List<SectorImpactDetail> findSectorImpacts(UUID eventId) {
        return jdbcTemplate.query(
            """
            SELECT si.sector_id, COALESCE(s.name, si.sector_name_raw) AS sector_name, si.direction, si.strength, si.confidence, si.mechanism
            FROM corporate.economic_event_sector_impacts si
            LEFT JOIN reference.sectors s ON s.id = si.sector_id
            WHERE si.event_id = ?
            """,
            (rs, rowNum) -> new SectorImpactDetail(
                (UUID) rs.getObject("sector_id"), rs.getString("sector_name"), rs.getString("direction"),
                rs.getString("strength"), rs.getDouble("confidence"), rs.getString("mechanism")
            ),
            eventId
        );
    }

    private List<CompanyExposureDetail> findCompanyExposures(UUID eventId) {
        return jdbcTemplate.query(
            """
            SELECT ce.matched_instrument_id, ce.matched_security_master_id,
                   COALESCE(i.symbol, sm.symbol) AS matched_symbol,
                   ce.company_name_raw, ce.match_type, ce.exposure_type, ce.direction, ce.impact_strength, ce.confidence, ce.reason
            FROM corporate.economic_event_company_exposures ce
            LEFT JOIN reference.instruments i ON i.id = ce.matched_instrument_id
            LEFT JOIN reference.security_master sm ON sm.id = ce.matched_security_master_id
            WHERE ce.event_id = ?
            """,
            (rs, rowNum) -> new CompanyExposureDetail(
                (UUID) rs.getObject("matched_instrument_id"), (UUID) rs.getObject("matched_security_master_id"),
                rs.getString("matched_symbol"), rs.getObject("matched_instrument_id") != null,
                rs.getString("company_name_raw"), rs.getString("match_type"), rs.getString("exposure_type"),
                rs.getString("direction"), rs.getString("impact_strength"), rs.getDouble("confidence"), rs.getString("reason")
            ),
            eventId
        );
    }

    private List<NewsSourceArticle> findSourceArticles(String clusterKey) {
        return jdbcTemplate.query(
            """
            SELECT DISTINCT d.id, d.title, d.source_url, d.announced_at
            FROM corporate.documents d
            JOIN corporate.economic_events e ON e.document_id = d.id
            WHERE e.cluster_key = ?
            ORDER BY d.announced_at DESC
            """,
            (rs, rowNum) -> new NewsSourceArticle(
                (UUID) rs.getObject("id"), rs.getString("title"), rs.getString("source_url"), rs.getTimestamp("announced_at").toInstant()
            ),
            clusterKey
        );
    }

    /**
     * One row per sector currently carrying a live (non-expired) impact - strongest direction/
     * strength wins per sector (HIGH > MEDIUM > LOW, ties broken by confidence), never averaged
     * across possibly-conflicting events for the same sector.
     */
    public List<SectorImpactMapEntry> findSectorImpactMap() {
        return jdbcTemplate.query(
            """
            SELECT DISTINCT ON (COALESCE(si.sector_id::text, si.sector_name_raw))
                   si.sector_id, COALESCE(s.name, si.sector_name_raw) AS sector_name, si.direction, si.strength,
                   (SELECT COUNT(DISTINCT si2.event_id) FROM corporate.economic_event_sector_impacts si2
                    JOIN corporate.economic_events e2 ON e2.id = si2.event_id
                    WHERE COALESCE(si2.sector_id::text, si2.sector_name_raw) = COALESCE(si.sector_id::text, si.sector_name_raw)
                      AND e2.expiry_date >= current_date) AS contributing_event_count
            FROM corporate.economic_event_sector_impacts si
            JOIN corporate.economic_events e ON e.id = si.event_id
            LEFT JOIN reference.sectors s ON s.id = si.sector_id
            WHERE e.expiry_date >= current_date
            ORDER BY COALESCE(si.sector_id::text, si.sector_name_raw),
                     CASE si.strength WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END DESC,
                     si.confidence DESC
            """,
            (rs, rowNum) -> new SectorImpactMapEntry(
                (UUID) rs.getObject("sector_id"), rs.getString("sector_name"), rs.getString("direction"),
                rs.getString("strength"), rs.getInt("contributing_event_count")
            )
        );
    }

    /** Every candidate, newest activity first. {@code tracked} is a live check against {@code reference.instruments} - a candidate can be promoted by other means after it was created here. */
    public List<NewsDiscoveryCandidate> findCandidates() {
        return jdbcTemplate.query(
            """
            SELECT c.symbol, c.company_name, c.security_master_id, c.first_seen_at, c.last_seen_at,
                   c.exposure_count, c.best_direction, c.best_exposure_type, c.status,
                   EXISTS (SELECT 1 FROM reference.instruments i WHERE i.symbol = c.symbol) AS tracked
            FROM corporate.news_discovery_candidates c
            ORDER BY c.last_seen_at DESC
            """,
            (rs, rowNum) -> new NewsDiscoveryCandidate(
                rs.getString("symbol"), rs.getString("company_name"), (UUID) rs.getObject("security_master_id"),
                rs.getBoolean("tracked"), rs.getTimestamp("first_seen_at").toInstant(), rs.getTimestamp("last_seen_at").toInstant(),
                rs.getInt("exposure_count"), rs.getString("best_direction"), rs.getString("best_exposure_type"), rs.getString("status")
            )
        );
    }
}
