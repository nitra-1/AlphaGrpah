package com.alphagraph.api.comparison;

import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionScoreReader;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Assembles a side-by-side comparison (Module 3.5) by calling decision.engine.DecisionScoreReader
 * directly for each requested instrument - the same api-assembles-from-domain-readers pattern
 * every prior read endpoint in this project uses. No new decision-module code was needed:
 * {@link DecisionScore} (Module 3.1) already carries every field a comparison needs.
 */
@Service
public class ComparisonService {

    private final DecisionScoreReader decisionScoreReader;
    private final JdbcTemplate jdbcTemplate;

    public ComparisonService(DecisionScoreReader decisionScoreReader, JdbcTemplate jdbcTemplate) {
        this.decisionScoreReader = decisionScoreReader;
        this.jdbcTemplate = jdbcTemplate;
    }

    /** Instrument ids that don't exist in reference.instruments at all are silently skipped - everything else is included, scored or not, sorted best-Swing-Rank first (nulls last). */
    public List<ComparisonEntryDto> compare(List<UUID> instrumentIds) {
        List<ComparisonEntryDto> entries = new ArrayList<>();
        for (UUID instrumentId : instrumentIds) {
            toDto(instrumentId).ifPresent(entries::add);
        }
        entries.sort(Comparator.comparing(ComparisonEntryDto::swingRank, Comparator.nullsLast(Comparator.naturalOrder())));
        return entries;
    }

    private Optional<ComparisonEntryDto> toDto(UUID instrumentId) {
        Optional<DecisionScore> score = decisionScoreReader.findLatest(instrumentId);
        if (score.isPresent()) {
            DecisionScore s = score.get();
            return Optional.of(new ComparisonEntryDto(
                s.instrumentId(), s.symbol(), s.asOfDate(),
                s.technicalScore(), s.fundamentalScore(), s.institutionalScore(), s.sectorScore(), s.riskScore(), s.corporateScore(),
                s.swingScore(), s.swingRating().name(), s.swingRank(),
                s.longTermScore(), s.longTermRating().name(), s.longTermRank(),
                s.confidence()
            ));
        }

        String symbol;
        try {
            symbol = jdbcTemplate.queryForObject("SELECT symbol FROM reference.instruments WHERE id = ?", String.class, instrumentId);
        } catch (EmptyResultDataAccessException e) {
            return Optional.empty();
        }
        return Optional.of(new ComparisonEntryDto(
            instrumentId, symbol, null,
            null, null, null, null, null, null,
            null, null, null,
            null, null, null,
            null
        ));
    }
}
