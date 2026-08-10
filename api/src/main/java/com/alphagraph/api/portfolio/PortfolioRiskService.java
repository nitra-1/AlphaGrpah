package com.alphagraph.api.portfolio;

import com.alphagraph.risk.api.RiskLevel;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Aggregates risk ACROSS the current portfolio (Module 3.4) - market-value-weighted Risk Score
 * and concentration (single-holding, sector), computed fresh on every request rather than a
 * scheduled daily snapshot: unlike decision_scores/corporate_scores, this has no meaningful
 * "as of date" independent of whatever the portfolio happens to hold right now - it changes the
 * instant a buy/sell happens, not on a daily cadence.
 *
 * <p>Concentration band thresholds (25/40/60% for a single holding, 30/50/70% for a sector) are
 * this module's own reasonable defaults - conventional portfolio-management rules of thumb, not
 * derived from any external file or configuration - disclosed here rather than hidden behind an
 * unexplained magic number. weightedRiskLevel reuses risk.engine.RiskEngine's exact classify()
 * band boundaries (>=80 VERY_LOW / >=60 LOW / >40 MEDIUM / >20 HIGH / else VERY_HIGH) so a
 * portfolio-level risk label means the same thing as a per-holding one - RiskEngine's classifier
 * itself isn't reusable (private, and risk depends on nothing outside itself), so this
 * independently mirrors it, the same way CorporateRating/DecisionRating each define their own
 * band logic despite the same 5-band shape.
 */
@Service
public class PortfolioRiskService {

    private final PortfolioViewService portfolioViewService;
    private final JdbcTemplate jdbcTemplate;

    public PortfolioRiskService(PortfolioViewService portfolioViewService, JdbcTemplate jdbcTemplate) {
        this.portfolioViewService = portfolioViewService;
        this.jdbcTemplate = jdbcTemplate;
    }

    public PortfolioRiskDto compute(UUID userId) {
        List<PortfolioEntryDto> priced = portfolioViewService.list(userId).stream()
            .filter(e -> e.marketValue() != null)
            .toList();

        if (priced.isEmpty()) {
            return new PortfolioRiskDto(null, null, null, null, null, null, null, null, null, null, List.of());
        }

        BigDecimal totalMarketValue = priced.stream().map(PortfolioEntryDto::marketValue).reduce(BigDecimal.ZERO, BigDecimal::add);

        PortfolioEntryDto topHolding = priced.stream().max(Comparator.comparing(PortfolioEntryDto::marketValue)).orElseThrow();
        BigDecimal topHoldingPercent = percentOf(topHolding.marketValue(), totalMarketValue);

        List<PortfolioEntryDto> risked = priced.stream().filter(e -> e.riskScore() != null).toList();
        BigDecimal riskedValue = risked.stream().map(PortfolioEntryDto::marketValue).reduce(BigDecimal.ZERO, BigDecimal::add);
        Double weightedRiskScore = risked.isEmpty() ? null : round2dp(risked.stream()
            .mapToDouble(e -> e.marketValue().doubleValue() * e.riskScore())
            .sum() / riskedValue.doubleValue());
        Double riskScoreCoveragePercent = risked.isEmpty() ? null : percentOf(riskedValue, totalMarketValue).doubleValue();

        Map<String, BigDecimal> valueBySector = new LinkedHashMap<>();
        for (PortfolioEntryDto entry : priced) {
            String sectorName = resolveSectorName(entry.instrumentId());
            valueBySector.merge(sectorName, entry.marketValue(), BigDecimal::add);
        }
        List<SectorExposureDto> sectorBreakdown = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> e : valueBySector.entrySet()) {
            sectorBreakdown.add(new SectorExposureDto(e.getKey(), e.getValue(), percentOf(e.getValue(), totalMarketValue)));
        }
        sectorBreakdown.sort(Comparator.comparing(SectorExposureDto::marketValue).reversed());
        SectorExposureDto topSector = sectorBreakdown.get(0);

        return new PortfolioRiskDto(
            totalMarketValue,
            weightedRiskScore, weightedRiskScore == null ? null : classifyRiskLevel(weightedRiskScore).name(), riskScoreCoveragePercent,
            topHolding.symbol(), topHoldingPercent, classifyHoldingConcentration(topHoldingPercent.doubleValue()).name(),
            topSector.sectorName(), topSector.percentOfPortfolio(), classifySectorConcentration(topSector.percentOfPortfolio().doubleValue()).name(),
            sectorBreakdown
        );
    }

    private String resolveSectorName(UUID instrumentId) {
        List<String> names = jdbcTemplate.query(
            "SELECT s.name FROM reference.instruments i JOIN reference.sectors s ON s.id = i.sector_id WHERE i.id = ?",
            (rs, rowNum) -> rs.getString("name"),
            instrumentId
        );
        return names.stream().findFirst().orElse("Unclassified");
    }

    private static BigDecimal percentOf(BigDecimal part, BigDecimal whole) {
        return part.divide(whole, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
    }

    /** Matches decision.engine.DecisionScoringEngine's own rounding rationale - a weighted average over doubles can leave sub-cent binary floating-point noise that has no business surviving into a displayed score. */
    private static double round2dp(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private enum ConcentrationLevel { LOW, MODERATE, HIGH, VERY_HIGH }

    private static ConcentrationLevel classifyHoldingConcentration(double percent) {
        if (percent >= 60) {
            return ConcentrationLevel.VERY_HIGH;
        }
        if (percent >= 40) {
            return ConcentrationLevel.HIGH;
        }
        if (percent >= 25) {
            return ConcentrationLevel.MODERATE;
        }
        return ConcentrationLevel.LOW;
    }

    private static ConcentrationLevel classifySectorConcentration(double percent) {
        if (percent >= 70) {
            return ConcentrationLevel.VERY_HIGH;
        }
        if (percent >= 50) {
            return ConcentrationLevel.HIGH;
        }
        if (percent >= 30) {
            return ConcentrationLevel.MODERATE;
        }
        return ConcentrationLevel.LOW;
    }

    private static RiskLevel classifyRiskLevel(double score) {
        if (score >= 80) {
            return RiskLevel.VERY_LOW;
        }
        if (score >= 60) {
            return RiskLevel.LOW;
        }
        if (score > 40) {
            return RiskLevel.MEDIUM;
        }
        if (score > 20) {
            return RiskLevel.HIGH;
        }
        return RiskLevel.VERY_HIGH;
    }
}
