package com.alphagraph.common.rules;

/**
 * Mirrors the {@code operator} CHECK constraint on {@code common.rule_conditions}
 * (docs/003_Database_Architecture.md §3) — keep the two in sync if either changes.
 */
public enum RuleOperator {
    GT,
    LT,
    GTE,
    LTE,
    EQ,
    BETWEEN
}
