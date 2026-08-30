-- Real bug caught live: POSSIBLE_ACCUMULATION/POSSIBLE_DISTRIBUTION are 21 characters, one over
-- V9's varchar(20) - every directional interpretation failed to write with "value too long for
-- type character varying(20)". Widened with margin rather than to the exact minimum.
ALTER TABLE ownership.institutional_interpretations ALTER COLUMN institutional_state TYPE varchar(30);
