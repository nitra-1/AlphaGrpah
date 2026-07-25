package com.alphagraph.common.rules;

import java.util.List;

/**
 * A versioned collection of {@link Rule}s, resolved at calculation time by whatever loads the
 * active rules from {@code common.rule_definitions} — see docs/002_Engine_Architecture.md §5.
 * The {@link com.alphagraph.common.engine.Engine} contract takes one of these instead of
 * hard-coding which rules apply.
 */
public record RuleSet(int version, List<Rule> rules) {

    public RuleSet {
        rules = List.copyOf(rules);
    }
}
