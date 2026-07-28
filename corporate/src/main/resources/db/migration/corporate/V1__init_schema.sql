CREATE SCHEMA IF NOT EXISTS corporate;

CREATE FUNCTION corporate.set_updated_at()
    RETURNS trigger
    LANGUAGE plpgsql
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

-- Same real-world gap as ownership/financial (Modules 1.2/1.3): NSE distributes its bulk
-- corporate actions report via its Extranet to clearing members only, not publicly. The
-- interactive site's per-symbol corporate-actions API is unofficial, requires a browser session,
-- and isn't a stable free bulk source either. This module ships against a manually-compiled
-- sample (real dividend ex-dates/record-dates/amounts, see Module 1.4 notes in claude.md) until a
-- real automated source is chosen.
--
-- Only instrument_id, action_type, and ex_date are required - those three apply to every
-- corporate action regardless of type. Everything else is type-specific and therefore nullable:
-- dividend_amount only applies to DIVIDEND, ratio_numerator/ratio_denominator only to
-- BONUS/SPLIT/RIGHTS, and price only to RIGHTS/BUYBACK.
CREATE TABLE corporate.corporate_actions (
    id                  uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id       uuid NOT NULL,
    action_type         varchar(10) NOT NULL,
    ex_date             date NOT NULL,
    record_date         date,
    announcement_date   date,
    dividend_amount     numeric(10, 4),
    ratio_numerator     integer,
    ratio_denominator   integer,
    price               numeric(12, 2),
    created_at          timestamptz NOT NULL DEFAULT now(),
    updated_at          timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ck_corporate_actions_action_type CHECK (action_type IN ('DIVIDEND', 'BONUS', 'SPLIT', 'RIGHTS', 'BUYBACK')),
    CONSTRAINT ux_corporate_actions_instrument_type_exdate UNIQUE (instrument_id, action_type, ex_date)
);

-- instrument_id references reference.instruments by value only — no cross-schema foreign key,
-- per docs/003_Database_Architecture.md §2.
CREATE INDEX ix_corporate_actions_instrument_id ON corporate.corporate_actions (instrument_id);
CREATE INDEX ix_corporate_actions_ex_date ON corporate.corporate_actions (ex_date);

CREATE TRIGGER trg_corporate_actions_updated_at
    BEFORE UPDATE ON corporate.corporate_actions
    FOR EACH ROW EXECUTE FUNCTION corporate.set_updated_at();
