package com.alphagraph.financial.transformation;

/**
 * Matches {@code financial.inflection_states.primary_state}'s CHECK constraint exactly. Not
 * mutually exclusive in what actually "fires" - {@link FinancialInflectionEngine} picks one of
 * these as {@code primaryState} via a hardcoded priority ladder, but every state that fired is
 * still recorded as a reason code, same convention every other family's Stage 2 already uses.
 *
 * <p>{@code EARNINGS_INFLECTION_CONVERGENCE}, not the spec's original {@code
 * MULTI_QUARTER_EARNINGS_ACCELERATION} - with only 5 real quarters of history, this state detects
 * three conditions converging in the same quarter, not acceleration genuinely persisting across
 * multiple comparable periods. That stronger name is reserved for when deeper history exists.
 */
enum FinancialInflectionState {
    NO_CLEAR_SIGNAL,
    REVENUE_ACCELERATION,
    PAT_ACCELERATION,
    STRUCTURAL_MARGIN_EXPANSION,
    OPERATING_LEVERAGE_INFLECTION,
    EARNINGS_INFLECTION_CONVERGENCE
}
