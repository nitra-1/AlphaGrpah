package com.alphagraph.corporate.news;

import com.alphagraph.common.engine.Engine;
import com.alphagraph.common.rules.ArithmeticRuleEvaluator;
import com.alphagraph.common.rules.EvaluationResult;
import com.alphagraph.common.rules.MetricContext;
import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleEvaluator;
import com.alphagraph.common.rules.RuleSet;
import com.alphagraph.corporate.api.NewsCatalystSnapshot;
import com.alphagraph.corporate.api.NewsCatalystTrend;
import com.alphagraph.corporate.api.NewsImpactDirection;
import com.alphagraph.corporate.api.NewsInstrumentLink;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Aggregates one instrument's news-catalyst link history (Layer 1) into a point-in-time
 * {@link NewsCatalystSnapshot} (Layer 2) - a genuine {@code common.engine.Engine} implementation,
 * same shape as {@code corporate.orderbook.OrderBookAggregationEngine} and
 * {@code corporate.commentary.ManagementCommentaryEngine}.
 *
 * <p>Unlike Management Commentary's quarterly-cadence guidance, news catalysts are episodic, so
 * there's no "persistence" analogue - the three signals are net direction across ALL available
 * link history (not windowed to a recent period - a real, disclosed simplification: a catalyst
 * from a year ago still counts toward net direction, though {@link #recencyDays} separately
 * penalizes staleness), how many independent links reinforce that direction (volume), and how
 * fresh the most recent one is (recency).
 */
@Component
class NewsCatalystEngine implements Engine<NewsCatalystInput, NewsCatalystSnapshot> {

    private final RuleEvaluator ruleEvaluator = new ArithmeticRuleEvaluator();

    @Override
    public NewsCatalystSnapshot calculate(NewsCatalystInput input, RuleSet rules) {
        List<NewsInstrumentLink> links = input.links();

        Map<String, Double> metrics = new HashMap<>();
        if (!links.isEmpty()) {
            metrics.put("newsCatalystNetDirection", netDirection(links));
            metrics.put("newsCatalystVolume", (double) links.size());
            metrics.put("newsCatalystRecencyDays", (double) recencyDays(links, input.asOfDate()));
        }

        MetricContext context = new MetricContext(metrics);
        double rawScore = 0.0;
        for (Rule rule : rules.rules()) {
            EvaluationResult result = ruleEvaluator.evaluate(rule, context);
            rawScore += result.contribution();
        }
        double catalystScore = clamp(50.0 + rawScore * 10.0, 0.0, 100.0);

        NewsCatalystTrend trend = computeTrend(links, metrics.get("newsCatalystNetDirection"));
        double confidence = confidence(links);

        return new NewsCatalystSnapshot(
            input.instrumentId(), input.symbol(), input.asOfDate(),
            catalystScore, trend, links.size(), confidence, rules.version(), Instant.now()
        );
    }

    private static double netDirection(List<NewsInstrumentLink> links) {
        double net = 0.0;
        for (NewsInstrumentLink link : links) {
            net += directionSignal(link.direction());
        }
        return net;
    }

    private static double directionSignal(NewsImpactDirection direction) {
        return switch (direction) {
            case POSITIVE -> 1.0;
            case NEGATIVE -> -1.0;
            case NEUTRAL -> 0.0;
        };
    }

    /** links is newest-first (see NewsLinkReader), so the first element is the most recent catalyst. */
    private static long recencyDays(List<NewsInstrumentLink> links, LocalDate asOfDate) {
        Instant mostRecent = links.get(0).announcedAt();
        LocalDate mostRecentDate = mostRecent.atZone(ZoneId.of("Asia/Kolkata")).toLocalDate();
        return ChronoUnit.DAYS.between(mostRecentDate, asOfDate);
    }

    private static NewsCatalystTrend computeTrend(List<NewsInstrumentLink> links, Double netDirection) {
        if (links.isEmpty() || netDirection == null) {
            return NewsCatalystTrend.NONE;
        }
        if (netDirection > 0) {
            return NewsCatalystTrend.POSITIVE;
        }
        if (netDirection < 0) {
            return NewsCatalystTrend.NEGATIVE;
        }
        return NewsCatalystTrend.MIXED;
    }

    private static double confidence(List<NewsInstrumentLink> links) {
        if (links.isEmpty()) {
            return 50.0;
        }
        return 50.0 + 50.0 * (Math.min(links.size(), 5) / 5.0);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
