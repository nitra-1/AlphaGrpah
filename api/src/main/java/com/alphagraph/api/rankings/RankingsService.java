package com.alphagraph.api.rankings;

import com.alphagraph.decision.api.DecisionScore;
import com.alphagraph.decision.engine.DecisionScoreReader;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RankingsService {

    private final DecisionScoreReader decisionScoreReader;

    public RankingsService(DecisionScoreReader decisionScoreReader) {
        this.decisionScoreReader = decisionScoreReader;
    }

    /** Every tracked instrument's latest score, highest Swing Score (best Rank) first. */
    public List<RankingEntryDto> list() {
        return decisionScoreReader.findAllLatest().stream().map(RankingsService::toDto).toList();
    }

    private static RankingEntryDto toDto(DecisionScore s) {
        return new RankingEntryDto(
            s.instrumentId(), s.symbol(), s.asOfDate(),
            s.technicalScore(), s.fundamentalScore(), s.institutionalScore(), s.sectorScore(), s.riskScore(), s.corporateScore(),
            s.swingScore(), s.swingRating().name(), s.swingRank(),
            s.longTermScore(), s.longTermRating().name(), s.longTermRank(),
            s.confidence()
        );
    }
}
