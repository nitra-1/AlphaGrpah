-- Closes a real, previously disclosed gap (Module 1.1's own note: "Security Master remains a
-- one-time seed (reference.instruments via migration), not an ongoing ingested pipeline - worth
-- revisiting if new listings/symbol changes need to flow in automatically"). This table is
-- deliberately NOT reference.instruments - it's the full NSE-listed equity universe (~2,400
-- symbols), used only as a lookup/autocomplete source so a non-technical admin can pick a real
-- symbol+ISIN by selecting from a dropdown instead of typing (and needing to independently
-- verify) either by hand. Actively TRACKED/scored instruments still live in
-- reference.instruments and still require the deliberate steps in
-- docs/006_Universe_Expansion_Runbook.md - adding a row here never implies tracking it.
CREATE TABLE reference.security_master (
    id             uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    symbol         text NOT NULL,
    company_name   text NOT NULL,
    series         varchar(10) NOT NULL,
    isin           text NOT NULL,
    listing_date   date,
    face_value     numeric,
    created_at     timestamptz NOT NULL DEFAULT now(),
    updated_at     timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_security_master_symbol UNIQUE (symbol)
);

CREATE INDEX ix_security_master_company_name ON reference.security_master (company_name);

CREATE TRIGGER trg_security_master_updated_at
    BEFORE UPDATE ON reference.security_master
    FOR EACH ROW EXECUTE FUNCTION reference.set_updated_at();
