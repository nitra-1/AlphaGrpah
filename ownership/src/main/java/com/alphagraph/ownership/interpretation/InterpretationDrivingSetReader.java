package com.alphagraph.ownership.interpretation;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * The orchestrator's driving set, locked as an explicit, non-contradictory condition: {@code
 * active discovery candidate (not DISMISSED, not promoted) OR (promoted AND NOT DISMISSED AND a
 * directional interpretation exists AND confirmation_frozen = false)}. {@code DISMISSED} always
 * stops processing outright in both branches (a symbol could in principle be dismissed after being
 * separately added to {@code reference.instruments}); a promoted symbol keeps maturing its
 * in-flight T+1/T+3/T+5 confirmation only until it freezes, at which point it drops out of the
 * driving set entirely - even though {@code ownership.deals.DiscoveryReader} (the visible queue)
 * drops a promoted symbol immediately, independent of this.
 */
@Component
class InterpretationDrivingSetReader {

    private final JdbcTemplate jdbcTemplate;

    InterpretationDrivingSetReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    List<String> findSymbolsToProcess() {
        return jdbcTemplate.query(
            """
            SELECT ds.symbol
            FROM ownership.discovery_status ds
            WHERE ds.status != 'DISMISSED'
              AND NOT EXISTS (SELECT 1 FROM reference.instruments i WHERE i.symbol = ds.symbol)

            UNION

            SELECT ds.symbol
            FROM ownership.discovery_status ds
            JOIN LATERAL (
                SELECT ii.institutional_state, ii.confirmation_frozen
                FROM ownership.institutional_interpretations ii
                WHERE ii.symbol = ds.symbol
                ORDER BY ii.as_of_date DESC
                LIMIT 1
            ) latest ON true
            WHERE ds.status != 'DISMISSED'
              AND EXISTS (SELECT 1 FROM reference.instruments i WHERE i.symbol = ds.symbol)
              AND latest.institutional_state IN ('POSSIBLE_ACCUMULATION', 'POSSIBLE_DISTRIBUTION')
              AND latest.confirmation_frozen = false
            """,
            (rs, rowNum) -> rs.getString("symbol")
        );
    }
}
