package com.alphagraph.api.rule;

import com.alphagraph.common.rules.RuleCondition;

import java.util.List;
import java.util.UUID;

public record RuleDefinitionDto(UUID id, String name, String targetMetric, int version, boolean active, List<RuleCondition> conditions) {
}
