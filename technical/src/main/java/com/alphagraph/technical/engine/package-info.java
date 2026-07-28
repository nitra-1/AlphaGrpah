/**
 * The Technical Engine itself: {@link com.alphagraph.technical.engine.TechnicalEngine} implements
 * {@code common.engine.Engine<TechnicalEngineInput, TechnicalScore>}, evaluating a RuleSet loaded
 * by {@link com.alphagraph.technical.engine.RuleSetLoader} against indicators computed from
 * {@code technical.indicators}/{@code technical.structure}. Persistence of the result is
 * {@link com.alphagraph.technical.engine.TechnicalScoreWriter} — the module's ScoreWriter per
 * docs/002_Engine_Architecture.md §5. The engine itself does no I/O: {@code intelligence} reads
 * market data, builds the input, calls {@code calculate()}, and hands the result to the writer.
 */
package com.alphagraph.technical.engine;
