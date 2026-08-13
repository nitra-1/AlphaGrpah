package com.alphagraph.learning.performance;

import com.alphagraph.decision.api.DecisionRating;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Phase 4A.5: Model Performance Monitor. Reads learning.forward_outcomes and reports whether
 * AlphaGraph's own swing/long-term ratings have actually been directionally correct, by rating
 * and horizon - gated on {@link #MIN_SAMPLE_SIZE} so a bucket with, say, 2 outcomes never reports
 * a fake 100% or 0%.
 */
@Component
public class ModelPerformanceReader {

    static final int MIN_SAMPLE_SIZE = 10;

    private static final RowMapper<RatingHitRate> HIT_RATE_MAPPER = (rs, rowNum) -> {
        int sampleSize = rs.getInt("sample_size");
        long correctCount = rs.getLong("correct_count");
        boolean sufficient = sampleSize >= MIN_SAMPLE_SIZE;
        Double hitRate = sufficient ? (correctCount * 100.0 / sampleSize) : null;
        return new RatingHitRate(DecisionRating.valueOf(rs.getString("rating")), rs.getInt("horizon_days"), sampleSize, hitRate, sufficient);
    };

    private final JdbcTemplate jdbcTemplate;

    public ModelPerformanceReader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public EvidenceSummary evidenceSummary() {
        Integer daysOfHistory = jdbcTemplate.queryForObject(
            "SELECT count(DISTINCT as_of_date) FROM learning.decision_snapshots", Integer.class
        );
        Long snapshotCount = jdbcTemplate.queryForObject("SELECT count(*) FROM learning.decision_snapshots", Long.class);
        Long outcomeCount = jdbcTemplate.queryForObject("SELECT count(*) FROM learning.forward_outcomes WHERE status = 'CURRENT'", Long.class);
        return new EvidenceSummary(daysOfHistory == null ? 0 : daysOfHistory, snapshotCount == null ? 0 : snapshotCount, outcomeCount == null ? 0 : outcomeCount);
    }

    public List<RatingHitRate> swingRatingHitRates() {
        return hitRatesFor("swing_rating", "swing_directionally_correct");
    }

    public List<RatingHitRate> longTermRatingHitRates() {
        return hitRatesFor("long_term_rating", "long_term_directionally_correct");
    }

    private List<RatingHitRate> hitRatesFor(String ratingColumn, String correctColumn) {
        return jdbcTemplate.query(
            """
            SELECT %s AS rating, horizon_days,
                   count(*) FILTER (WHERE %s IS NOT NULL) AS sample_size,
                   count(*) FILTER (WHERE %s = true) AS correct_count
            FROM learning.forward_outcomes
            WHERE status = 'CURRENT'
            GROUP BY %s, horizon_days
            ORDER BY %s, horizon_days
            """.formatted(ratingColumn, correctColumn, correctColumn, ratingColumn, ratingColumn),
            HIT_RATE_MAPPER
        );
    }
}
