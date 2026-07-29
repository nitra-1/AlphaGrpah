-- Module 1.6 (Fundamental Engine): Efficiency (Asset Turnover, Working Capital, Cash Conversion)
-- and Leverage (Debt, Interest Coverage, Debt/Equity) need real balance sheet fields that
-- financial_results never carried - it only had P&L-style metrics from Module 1.3. Added as new
-- nullable columns on the existing table rather than a separate table: one row per
-- (instrument, period_end, period_type) already represents "everything known about this company
-- for this period", and Efficiency/Leverage are meaningless without the same period's P&L
-- figures anyway (Asset Turnover = Sales / Total Assets, Interest Coverage = EBIT / Interest
-- Expense) - splitting them into a second table would just force a join back to this one.
--
-- All nullable, and populated for FY25 (2025-03-31) only, not the Q4 FY24 comparison rows added
-- alongside this migration (Module 1.6 needed a second period per company to compute Growth -
-- see the CSV) - a balance sheet snapshot isn't meaningful attached to a prior comparison period
-- the engine only reads Sales/PAT/EPS from.
--
-- Deliberately left null for HDFCBANK/ICICIBANK: a bank's balance sheet is dominated by
-- deposits/advances, not inventory/receivables/payables - "current assets", "current
-- liabilities", and even "debt" in the conventional working-capital sense don't map onto a bank
-- without a sector-aware model that doesn't exist yet (same caveat class as Module 1.3's
-- NII-as-sales substitution for banks).
ALTER TABLE financial.financial_results
    ADD COLUMN total_assets         numeric(16, 2),
    ADD COLUMN current_assets       numeric(16, 2),
    ADD COLUMN current_liabilities  numeric(16, 2),
    ADD COLUMN total_debt           numeric(16, 2),
    ADD COLUMN total_equity         numeric(16, 2),
    ADD COLUMN interest_expense     numeric(14, 2),
    ADD COLUMN ebit                 numeric(14, 2);
