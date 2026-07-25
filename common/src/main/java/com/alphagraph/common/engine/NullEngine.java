package com.alphagraph.common.engine;

import com.alphagraph.common.rules.RuleSet;

import java.time.Instant;

/**
 * Phase 0's only {@link Engine} implementation: proves the scheduler's Calculate -> Score ->
 * Notify stages execute end to end before any real engine exists. Not a placeholder for real
 * scoring logic — it is deliberately inert.
 */
public final class NullEngine implements Engine<Object, SimpleScore> {

    @Override
    public SimpleScore calculate(Object input, RuleSet rules) {
        return new SimpleScore(0.0, 0.0, rules.version(), Instant.now());
    }
}
