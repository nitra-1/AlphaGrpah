package com.alphagraph.api.rule;

import com.alphagraph.common.rules.RuleCondition;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;

/**
 * Reuses common.rules.RuleCondition directly instead of a parallel DTO with identical fields -
 * it's already a plain value record (not a JPA entity), and its own constructor already
 * validates the BETWEEN/upperBound rule, so a malformed condition 400s before this even
 * reaches the repository.
 */
public record CreateRuleRequest(
    @NotBlank String name,
    @NotBlank String targetMetric,
    @NotEmpty java.util.List<RuleCondition> conditions
) {
}
