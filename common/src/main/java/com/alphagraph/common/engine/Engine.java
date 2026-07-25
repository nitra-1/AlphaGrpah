package com.alphagraph.common.engine;

import com.alphagraph.common.rules.RuleSet;

/**
 * Contract every Phase 1+ scoring engine implements. {@code rules} is resolved via the Rule
 * Engine at calculation time rather than hard-coded, per docs/002_Engine_Architecture.md §5.
 */
public interface Engine<I, O extends Score> {

    O calculate(I input, RuleSet rules);
}
