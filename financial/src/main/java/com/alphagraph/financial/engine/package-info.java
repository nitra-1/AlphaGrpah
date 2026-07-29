/**
 * The Fundamental Engine: {@link com.alphagraph.financial.engine.FundamentalEngine} implements
 * {@code common.engine.Engine<FundamentalEngineInput, FundamentalScore>}, evaluating a RuleSet
 * loaded by {@link com.alphagraph.financial.engine.FundamentalRuleSetLoader} against metrics derived from
 * {@code financial.financial_results}. Unlike the Technical Engine (Module 1.5), this stays
 * entirely inside the {@code financial} module: the data it reads is already {@code financial}'s
 * own (Module 1.3), not another domain module's, so there's no {@code intelligence}-bridging
 * needed - {@link com.alphagraph.financial.engine.FundamentalAnalysisOrchestrator} reads via
 * {@link com.alphagraph.financial.engine.FinancialResultReader} and writes via
 * {@link com.alphagraph.financial.engine.FundamentalScoreWriter} directly.
 */
package com.alphagraph.financial.engine;
