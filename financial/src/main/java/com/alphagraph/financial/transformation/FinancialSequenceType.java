package com.alphagraph.financial.transformation;

/**
 * Matches {@code financial.transformation_sequences.sequence_type}'s CHECK constraint exactly. An
 * instrument may legitimately have multiple of these active simultaneously - no priority ladder.
 * A 5th group ({@code DELEVERAGING_CYCLE}/{@code BALANCE_SHEET_REPAIR}/{@code CASH_FLOW_TURNAROUND})
 * stays reserved, not implemented (docs/008 §14.1) - no {@code DEBT_LEVEL}/
 * {@code CASH_FLOW_FROM_OPERATIONS} Stage 1 evidence exists yet.
 */
enum FinancialSequenceType {
    BUSINESS_ACCELERATION_CYCLE,
    OPERATING_LEVERAGE_CYCLE,
    MULTI_QUARTER_EARNINGS_EXPANSION,
    INTEREST_COST_RELIEF_TREND
}
