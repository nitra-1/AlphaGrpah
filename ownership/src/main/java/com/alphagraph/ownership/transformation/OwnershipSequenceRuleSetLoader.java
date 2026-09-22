package com.alphagraph.ownership.transformation;

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
 * Loads the 6 named Stage 3 Ownership sequence thresholds from
 * {@code common.rule_definitions}/{@code rule_conditions} - same loading shape as
 * {@code market.transformation.MarketSequenceRuleSetLoader}: named scalar constants
 * (single-{@code ALWAYS}-condition rules), never a metric-scoring ladder. Java reads the stored
 * {@code threshold} back directly via {@link #ruleThreshold}, never runs it through
 * {@code ArithmeticRuleEvaluator}.
 */
@Component
class OwnershipSequenceRuleSetLoader {

    private static final List<String> RULE_NAMES = List.of(
        "stage3-ownership-building-max-gap", "stage3-ownership-building-min-persistence",
        "stage3-ownership-contradiction-tolerance", "stage3-broad-participation-max-span",
        "stage3-promoter-alignment-max-span", "stage3-ownership-sequence-max-age"
    );

    private final JdbcTemplate jdbcTemplate;

    OwnershipSequenceRuleSetLoader(JdbcTemplate jdbcTemplate) {
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
    static int ruleThreshold(RuleSet rules, String ruleName, int defaultValue) {
        return rules.rules().stream()
            .filter(r -> r.name().equals(ruleName))
            .findFirst()
            .flatMap(r -> r.conditions().stream().findFirst())
            .map(c -> (int) Math.round(c.threshold()))
            .orElse(defaultValue);
    }
}
