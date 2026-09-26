package com.alphagraph.corporate.news;

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
 * Loads the {@code news-expiry-days-*} named-scalar-constant rules (half-life v1 - a simple fixed
 * cutoff per event magnitude, not a continuously-recomputed decay curve) - same
 * single-ALWAYS-condition convention as Stage 3/4/5's own RuleSetLoaders (e.g. {@code
 * discovery.lifecycle.DiscoveryLifecycleRuleSetLoader}), not {@code
 * corporate.news.NewsCatalystRuleSetLoader}'s own weighted-scoring-ladder convention - a
 * legitimate, small, intentional duplication of the read shape, same reason every domain's own
 * RuleSetLoader can't share code across packages.
 */
@Component
class EconomicEventRuleSetLoader {

    private final JdbcTemplate jdbcTemplate;

    EconomicEventRuleSetLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    RuleSet loadActiveRules() {
        List<Map<String, Object>> ruleRows = jdbcTemplate.queryForList(
            "SELECT id, name, target_metric, version FROM common.rule_definitions " +
            "WHERE active = true AND name LIKE 'news-expiry-days-%'"
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
        Double threshold = rules.rules().stream()
            .filter(r -> r.name().equals(ruleName))
            .findFirst()
            .flatMap(r -> r.conditions().stream().findFirst())
            .map(RuleCondition::threshold)
            .orElse(null);
        return threshold == null ? defaultValue : threshold.intValue();
    }
}
