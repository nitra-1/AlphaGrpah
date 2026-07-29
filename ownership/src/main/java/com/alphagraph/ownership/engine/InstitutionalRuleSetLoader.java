package com.alphagraph.ownership.engine;

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
 * Loads the active institutional-* rules from common.rule_definitions/rule_conditions into a
 * {@link RuleSet} - same pattern as technical.engine.RuleSetLoader (Module 1.5) and
 * financial.engine.FundamentalRuleSetLoader (Module 1.6). Named distinctly (not just
 * "RuleSetLoader") for the same reason those two were - Spring assigns bean names from the simple
 * class name by default, and two of those three modules already collided on it once before
 * (Module 1.6 rediscovering the Module 1.2 lesson).
 */
@Component
public class InstitutionalRuleSetLoader {

    private final JdbcTemplate jdbcTemplate;

    public InstitutionalRuleSetLoader(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public RuleSet loadActiveInstitutionalRules() {
        List<Map<String, Object>> ruleRows = jdbcTemplate.queryForList(
            "SELECT id, name, target_metric, version FROM common.rule_definitions " +
            "WHERE active = true AND name LIKE 'institutional-%'"
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
                "SELECT operator, threshold, upper_bound, weight FROM common.rule_conditions WHERE rule_id = ?",
                ruleId
            );
            List<RuleCondition> conditions = new ArrayList<>();
            for (Map<String, Object> c : conditionRows) {
                RuleOperator operator = RuleOperator.valueOf((String) c.get("operator"));
                BigDecimal threshold = (BigDecimal) c.get("threshold");
                BigDecimal upperBound = (BigDecimal) c.get("upper_bound");
                BigDecimal weight = (BigDecimal) c.get("weight");
                conditions.add(new RuleCondition(
                    operator, threshold.doubleValue(), upperBound == null ? null : upperBound.doubleValue(), weight.doubleValue()
                ));
            }
            rules.add(new Rule(name, targetMetric, version, conditions));
        }

        return new RuleSet(maxVersion, rules);
    }
}
