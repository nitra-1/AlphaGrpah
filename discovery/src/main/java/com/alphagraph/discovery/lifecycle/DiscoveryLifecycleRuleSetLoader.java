package com.alphagraph.discovery.lifecycle;

import com.alphagraph.common.rules.Rule;
import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import com.alphagraph.common.rules.RuleSet;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Loads the 23 named Stage 5 lifecycle thresholds from {@code common.rule_definitions}/
 * {@code rule_conditions} - same loading shape as every domain's own {@code *SequenceRuleSetLoader}
 * / {@code DiscoveryConvergenceRuleSetLoader}: named scalar constants (single-{@code ALWAYS}-
 * condition rules), never a metric-scoring ladder. Package-private visibility means the identical
 * {@code ruleThreshold} helper on {@code discovery.convergence.DiscoveryConvergenceRuleSetLoader}
 * can't be reused from this package either (same reason every reader in this feature is its own
 * copy) - a legitimate, small, intentional duplication, not an oversight.
 */
@Component
class DiscoveryLifecycleRuleSetLoader {

    private static final List<String> RULE_NAMES = List.of(
        "stage5-min-ready-observations", "stage5-min-history-span-days",
        "stage5-short-window-observations", "stage5-medium-window-observations", "stage5-long-window-observations",
        "stage5-early-inflection-min-persistence", "stage5-emerging-min-persistence",
        "stage5-accelerating-min-persistence", "stage5-market-recognition-min-persistence",
        "stage5-mature-rerating-min-days", "stage5-deterioration-min-persistence",
        "stage5-market-recognition-min-market-contribution",
        "stage5-rising-score-delta", "stage5-weakening-score-delta",
        "stage5-breadth-expansion-min-delta", "stage5-breadth-contraction-min-delta",
        "stage5-acceleration-entry-threshold", "stage5-acceleration-exit-threshold",
        "stage5-dormant-min-observations",
        "stage5-weight-persistence", "stage5-weight-evidence-confidence",
        "stage5-weight-trajectory-consistency", "stage5-weight-history-quality"
    );

    private final JdbcTemplate jdbcTemplate;

    DiscoveryLifecycleRuleSetLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    RuleSet loadActiveRules() {
        String placeholders = String.join(",", RULE_NAMES.stream().map(n -> "?").toList());
        List<Map<String, Object>> ruleRows = jdbcTemplate.queryForList(
            "SELECT id, name, target_metric, version FROM common.rule_definitions " +
            "WHERE active = true AND name IN (" + placeholders + ")",
            RULE_NAMES.toArray()
        );

        int maxVersion = 0;
        List<Rule> rules = new ArrayList<>();
        for (Map<String, Object> row : ruleRows) {
            UUID ruleId = (UUID) row.get("id");
            String name = (String) row.get("name");
            String targetMetric = (String) row.get("target_metric");
            int version = (Integer) row.get("version");
            maxVersion = Math.max(maxVersion, version);

            List<Map<String, Object>> conditionRows = jdbcTemplate.queryForList(
                "SELECT operator, threshold, upper_bound, weight FROM common.rule_conditions WHERE rule_id = ?", ruleId
            );
            List<RuleCondition> conditions = new ArrayList<>();
            for (Map<String, Object> c : conditionRows) {
                RuleOperator operator = RuleOperator.valueOf((String) c.get("operator"));
                BigDecimal threshold = (BigDecimal) c.get("threshold");
                BigDecimal upperBound = (BigDecimal) c.get("upper_bound");
                BigDecimal weight = (BigDecimal) c.get("weight");
                conditions.add(new RuleCondition(operator, threshold.doubleValue(), upperBound == null ? null : upperBound.doubleValue(), weight.doubleValue()));
            }
            rules.add(new Rule(name, targetMetric, version, conditions));
        }

        return new RuleSet(maxVersion, rules);
    }

    /** Reads a named rule's single ALWAYS condition's threshold directly - not a metric evaluation, just a stored constant lookup. Falls back to {@code defaultValue} if the rule is missing or inactive. */
    static double ruleThreshold(RuleSet rules, String ruleName, double defaultValue) {
        return rules.rules().stream()
            .filter(r -> r.name().equals(ruleName))
            .findFirst()
            .flatMap(r -> r.conditions().stream().findFirst())
            .map(RuleCondition::threshold)
            .orElse(defaultValue);
    }
}
