-- Real bug caught live during the LENSKART investigation: NSE's bulk-deal and block-deal reports
-- are independent disclosures, and the exact same real trade can genuinely qualify for and get
-- reported in *both* (a single large trade crossing both the 0.5%-of-shares bulk threshold and
-- the block-deal minimum-value threshold). ownership.discovered_deals' own unique key includes
-- deal_type, so both reports land as two separate rows by design - correct for the raw audit log,
-- but a real distortion if both are summed into any symbol-level aggregate (confirmed live: a
-- duplicated SELL of ~Rs.620cr flipped LENSKART's apparent net flow from buying to selling).
--
-- Both rows stay in the table permanently - nothing is deleted, this is purely a marker column so
-- aggregate readers can exclude the duplicate while DealDetailReader keeps showing every raw row
-- for audit. duplicate_of_deal_id NULL means "this row counts on its own" (either genuinely
-- unique, or the earlier-ingested of a duplicate pair) - non-null points at the row it duplicates.
--
-- Existing data backfilled here: for any two rows sharing (symbol, deal_date, client_name,
-- buy_sell, quantity, price) but a different deal_type, the later-ingested one is marked as the
-- duplicate of the earlier one - a deterministic (ingested_at, id) tie-break.
ALTER TABLE ownership.discovered_deals ADD COLUMN duplicate_of_deal_id uuid NULL REFERENCES ownership.discovered_deals (id);

UPDATE ownership.discovered_deals d1
SET duplicate_of_deal_id = d2.id
FROM ownership.discovered_deals d2
WHERE d1.symbol = d2.symbol
  AND d1.deal_date = d2.deal_date
  AND d1.client_name = d2.client_name
  AND d1.buy_sell = d2.buy_sell
  AND d1.quantity = d2.quantity
  AND d1.price = d2.price
  AND d1.deal_type != d2.deal_type
  AND (d1.ingested_at, d1.id) > (d2.ingested_at, d2.id)
  AND d1.duplicate_of_deal_id IS NULL;

CREATE INDEX ix_discovered_deals_duplicate_of ON ownership.discovered_deals (duplicate_of_deal_id);
