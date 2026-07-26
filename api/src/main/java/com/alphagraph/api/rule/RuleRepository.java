package com.alphagraph.api.rule;

import com.alphagraph.common.rules.RuleCondition;
import com.alphagraph.common.rules.RuleOperator;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Rule CRUD + activate/deactivate against common.rule_definitions/rule_conditions. */
@Repository
public class RuleRepository {

    private record RuleRow(UUID id, String name, String targetMetric, int version, boolean active) {
    }

    private final JdbcTemplate jdbcTemplate;

    public RuleRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<RuleDefinitionDto> findAll() {
        List<RuleRow> ruleRows = jdbcTemplate.query(
            "SELECT id, name, target_metric, version, active FROM common.rule_definitions ORDER BY name, version",
            (rs, rowNum) -> new RuleRow(
                (UUID) rs.getObject("id"), rs.getString("name"), rs.getString("target_metric"),
                rs.getInt("version"), rs.getBoolean("active")
            )
        );
        return ruleRows.stream()
            .map(row -> new RuleDefinitionDto(row.id(), row.name(), row.targetMetric(), row.version(), row.active(), findConditions(row.id())))
            .toList();
    }

    public RuleDefinitionDto create(CreateRuleRequest request) {
        int nextVersion = jdbcTemplate.queryForObject(
            "SELECT COALESCE(MAX(version), 0) FROM common.rule_definitions WHERE name = ?",
            Integer.class, request.name()
        ) + 1;

        UUID id = UUID.randomUUID();
        jdbcTemplate.update(
            "INSERT INTO common.rule_definitions (id, name, target_metric, version, active) VALUES (?, ?, ?, ?, false)",
            id, request.name(), request.targetMetric(), nextVersion
        );

        for (RuleCondition condition : request.conditions()) {
            jdbcTemplate.update(
                "INSERT INTO common.rule_conditions (id, rule_id, operator, threshold, upper_bound, weight) VALUES (?, ?, ?, ?, ?, ?)",
                UUID.randomUUID(), id, condition.operator().name(), condition.threshold(), condition.upperBound(), condition.weight()
            );
        }

        return new RuleDefinitionDto(id, request.name(), request.targetMetric(), nextVersion, false, request.conditions());
    }

    /** Atomic so a concurrent activate can never leave two active rows for the same name. */
    @Transactional
    public Optional<RuleDefinitionDto> activate(UUID id) {
        Optional<String> name = jdbcTemplate.query(
            "SELECT name FROM common.rule_definitions WHERE id = ?",
            (rs, rowNum) -> rs.getString("name"), id
        ).stream().findFirst();

        if (name.isEmpty()) {
            return Optional.empty();
        }

        jdbcTemplate.update("UPDATE common.rule_definitions SET active = false WHERE name = ? AND active = true", name.get());
        jdbcTemplate.update("UPDATE common.rule_definitions SET active = true WHERE id = ?", id);

        return findAll().stream().filter(rule -> rule.id().equals(id)).findFirst();
    }

    private List<RuleCondition> findConditions(UUID ruleId) {
        return jdbcTemplate.query(
            "SELECT operator, threshold, upper_bound, weight FROM common.rule_conditions WHERE rule_id = ?",
            (rs, rowNum) -> {
                BigDecimal upperBound = rs.getBigDecimal("upper_bound");
                return new RuleCondition(
                    RuleOperator.valueOf(rs.getString("operator")),
                    rs.getDouble("threshold"),
                    upperBound == null ? null : upperBound.doubleValue(),
                    rs.getDouble("weight")
                );
            },
            ruleId
        );
    }
}
