package com.alphagraph.ownership.engine;

import com.alphagraph.ownership.api.InstitutionalScore;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.util.UUID;

/**
 * Upserts on (instrument_id, as_of_date) — idempotent, matching every other module's
 * Loader/ScoreWriter convention (docs/002_Engine_Architecture.md §5).
 */
@Component
public class InstitutionalScoreWriter {

    private final JdbcTemplate jdbcTemplate;

    public InstitutionalScoreWriter(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public void write(InstitutionalScore score) {
        jdbcTemplate.update(
            """
            INSERT INTO ownership.institutional_scores
                (id, instrument_id, as_of_date, promoter_status, fii_status, dii_status, mf_status,
                 delivery_status, institutional_behaviour, institutional_score, confidence,
                 promoter_percentage, promoter_change_percentage, fii_change_percentage,
                 dii_change_percentage, mf_change_percentage, avg_delivery_percentage,
                 relative_volume, net_bulk_deal_quantity, rule_set_version, computed_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (instrument_id, as_of_date) DO UPDATE SET
                promoter_status = EXCLUDED.promoter_status, fii_status = EXCLUDED.fii_status,
                dii_status = EXCLUDED.dii_status, mf_status = EXCLUDED.mf_status,
                delivery_status = EXCLUDED.delivery_status, institutional_behaviour = EXCLUDED.institutional_behaviour,
                institutional_score = EXCLUDED.institutional_score, confidence = EXCLUDED.confidence,
                promoter_percentage = EXCLUDED.promoter_percentage,
                promoter_change_percentage = EXCLUDED.promoter_change_percentage,
                fii_change_percentage = EXCLUDED.fii_change_percentage,
                dii_change_percentage = EXCLUDED.dii_change_percentage,
                mf_change_percentage = EXCLUDED.mf_change_percentage,
                avg_delivery_percentage = EXCLUDED.avg_delivery_percentage,
                relative_volume = EXCLUDED.relative_volume, net_bulk_deal_quantity = EXCLUDED.net_bulk_deal_quantity,
                rule_set_version = EXCLUDED.rule_set_version, computed_at = EXCLUDED.computed_at
            """,
            UUID.randomUUID(), score.instrumentId(), score.asOfDate(), score.promoterStatus().name(),
            score.fiiStatus().name(), score.diiStatus().name(), score.mfStatus().name(),
            score.deliveryStatus().name(), score.institutionalBehaviour().name(),
            score.institutionalScore(), score.confidence(), score.promoterPercentage(),
            score.promoterChangePercentage(), score.fiiChangePercentage(), score.diiChangePercentage(),
            score.mfChangePercentage(), score.avgDeliveryPercentage(), score.relativeVolume(),
            score.netBulkDealQuantity(), score.ruleSetVersion(), Timestamp.from(score.computedAt())
        );
    }
}
