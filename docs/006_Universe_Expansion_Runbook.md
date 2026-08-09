# 006 — Universe Expansion Runbook

## 1. Purpose

Documents the manual process for adding a new tracked instrument (symbol) to AlphaGraph, following exactly what was done for the "batch 1" expansion from 8 to 20 instruments (2026-08-08). This is an operational runbook, not architecture — read [001_System_Architecture](001_System_Architecture.md) for how the modules fit together.

Adding a symbol has no automated path today. Confirmed live during batch 1: once a symbol exists in `reference.instruments`, daily price collection (`HttpBhavdataCollector`) and NSE announcement/RSS news matching pick it up automatically with zero code change (both filter the live market-wide feed / query `reference.instruments` fresh on every run). Everything else in this document — symbol selection, ISIN verification, historical backfill, and all financial/shareholding data — has no automated equivalent and needs a human (or an agent acting as one) to follow this process each time.

## 2. Before you start

- **Never fabricate a number.** Every figure in this process must be sourced and citable, or left `NULL`. This is the one non-negotiable rule — violating it corrupts the dataset in a way that's hard to detect later. See §5.
- **Avoid symbols mid-corporate-action.** TATAMOTORS was deliberately skipped during batch 1 because of an active Oct–Nov 2025 demerger (its old symbol/ISIN now maps to a different entity, TMPV, with a separately-listed spinoff, TMCV). Backfilling clean price history through a discontinuity isn't worth the complexity — pick a different candidate if one is mid-split, mid-merger, or recently relisted.
- **Batch small.** Financial/shareholding research is genuinely manual per company — batch1 did 12 at once via 3 parallel research passes (4 companies each) and that was already a lot to keep consistent. Don't do more than ~12–15 in one pass.

## 3. Step-by-step process

### Step 1 — Select symbols and verify ISINs

Pick real NSE-listed symbols, ideally filling sector gaps in the current universe rather than duplicating coverage (check `reference.sectors` for which sectors currently have zero or one constituent). For each candidate, verify via WebSearch (not from training-data recall):
- NSE trading symbol (exact spelling — note `M&M` for Mahindra & Mahindra includes the ampersand)
- Full legal company name
- ISIN (cross-check against at least one broker/data site that embeds the ISIN in its own URL path, e.g. `investonline.in/markets/stocks/<ISIN>/...` — a strong corroboration signal)

Batch 1's disclosed mistake class to avoid: don't trust a single source's ISIN without cross-checking — Module 1.1 originally shipped a fabricated BEML ISIN that had to be fixed in a follow-up migration.

### Step 2 — Add to reference data (migration)

New Flyway migration in `reference/src/main/resources/db/migration/reference/`, next available `V<n>`. Two parts:

1. Insert into `reference.instruments` (symbol, exchange_id via `WHERE e.code = 'NSE'`, name, isin, instrument_type='EQUITY'), `ON CONFLICT (symbol, exchange_id) DO NOTHING`.
2. Insert any new rows needed into `reference.sectors`, then `UPDATE reference.instruments SET sector_id = ...` per symbol. Check for an existing sector row by name before inserting a duplicate — `reference.sectors` has no unique constraint on `name`.

See `V5__expand_universe_batch1.sql` for the exact template. Restart the backend (`SPRING_PROFILES_ACTIVE=local ./gradlew :bootstrap:bootRun`) to apply via Flyway, then verify with `\d reference.instruments` / a `SELECT` join against `reference.sectors`.

### Step 3 — Backfill price history

Locally, `BhavdataCollector` (the `!docker & !prod` profile bean) reads a bundled sample CSV fixed to whatever instruments were in it when built — it will **not** pick up a new symbol on its own, even after Step 2. (Only `HttpBhavdataCollector`, active under `docker`/`prod`, reads live and auto-scales.) So a newly added symbol needs its own historical backfill or it starts with zero price history — no Technical/Sector score is possible until then.

Fetch real historical data directly from NSE's live archive, one request per trading day:

```
https://archives.nseindia.com/products/content/sec_bhavdata_full_<DDMMYYYY>.csv
```
(User-Agent header required, e.g. `Mozilla/5.0 (compatible; AlphaGraph/1.0)`.) Filter each day's CSV to rows matching `^<SYMBOL>, EQ,` — **anchor the match to the start of the line**, or a longer symbol name can false-match as a substring (e.g. an unanchored search for `ITC, EQ,` would wrongly match `XITC, EQ,`). Match the same date window the rest of the universe already has, so all instruments share one consistent history range.

Two real gotchas hit during batch 1, both worth checking for every time:
- **NSE doesn't 404 a market holiday** — it silently re-serves the prior real trading day's file under that date's URL, same internal date field and all. This shows up as duplicate `(symbol, date)` rows in the raw fetch. Compare the count of URLs fetched against the count of *distinct* dates in the result before writing the migration; the `ON CONFLICT (instrument_id, trade_date) DO NOTHING` on `market.daily_prices` will silently absorb the duplicates either way, but the migration's own comment should state the real distinct-day count, not the URL-fetch count.
- **Column mapping**: use `CLOSE_PRICE` (official close), not `LAST_PRICE`. `DELIV_PER` → `delivery_percentage`, `TTL_TRD_QNTY` → `volume`.

Write the result as a new migration in `market/src/main/resources/db/migration/market/`, matching `V2__seed_historical_daily_prices.sql`'s `INSERT ... SELECT ... FROM (VALUES ...) JOIN reference.instruments` shape (see `V3__backfill_universe_batch1_prices.sql`). Note `sma200`/`weekly_sma30` will stay unavailable until ~200/~150 real trading days accumulate — a disclosed, not fixable-by-backfill-alone, limitation shared with the original 8.

### Step 4 — Research financial results and shareholding data

Both `FinancialResultsCollector` and `ShareholdingCollector` are bundled-CSV-only by design — there is no free live bulk source for either in India (confirmed, disclosed limitation, not a gap to "just fix"). This step is genuinely manual every time.

**Sourcing.** Use WebSearch against multiple independent sources per company — screener.in, stockanalysis.com, trendlyne.com, Business Standard/other financial press, and the company's own investor-relations press releases/PDFs where available. For each company, look for:
- Most recent published **quarterly** results (same period as the rest of the universe, e.g. Q4 FY25 ending 2025-03-31, so the dataset stays comparable) — sales, PAT, EPS, ROE%, ROCE%, operating margin%, net margin%, cash flow from ops, and balance sheet items (total assets, current assets, current liabilities, total debt, total equity, interest expense, EBIT).
- Most recent published **shareholding pattern** — promoter/FII/DII/MF/public %.

For a batch of more than ~4–5 companies, splitting the research across parallel passes (one per sub-batch) keeps it faster without sacrificing per-company diligence — each pass should still independently cross-check its own sources rather than trust a single fetch.

**Rules for reconciling conflicting sources** (apply consistently, and disclose which rule was applied in the migration/CSV commit — don't silently pick):

| Situation | Rule |
|---|---|
| Two sources give clearly different PAT figures (pre- vs. post-minority-interest) | Use the figure that reconciles with the reported EPS ("owners' PAT") — this was the standard case for M&M, Tata Steel, UltraTech, NTPC, Asian Paints, L&T in batch 1 |
| Two sources give a genuinely conflicting figure with no way to tell which is right (e.g. the same tool returning both 24% and 31% ROE for the same company) | Leave the field `NULL`. Never average or guess. Same discipline as Module 1.6's TCS EPS `NULL` |
| A figure is real but distorted by a one-off event (e.g. ITC's Q4 FY25 PAT inflated ~4x by a hotel-demerger gain) | Store the real reported number as-is (don't invent an "adjusted" figure — that's editorializing beyond what was verified), but leave any *derived* field that the one-off would badly distort (e.g. net margin %) `NULL` rather than store a misleading value |
| A company genuinely has 0% promoter holding (ITC, L&T — no promoter / professionally managed) | Store real `0.00`, not `NULL` — this is a fact about the company, not missing data |
| A bank's balance sheet doesn't map to non-financial-company fields (current assets/liabilities, debt, EBIT don't mean the same thing for a bank) | Leave those fields `NULL` for banks entirely — same precedent as HDFCBANK/ICICIBANK in the original 8 |
| Shareholding data isn't available for the same period as the financial results | Use whatever the most recent real published period is per company and record that period_end — `ownership.shareholding_pattern` has its own independent `period_end` column, it doesn't need to match `financial.financial_results`' period |

### Step 5 — Load and verify

Append the researched rows to the bundled CSVs (format in §4 below) — do not write a raw SQL `INSERT` for this data, use the existing pipeline so it goes through the same code path real data would. **Restart the backend after editing the CSVs** — they're classpath resources, and a running JVM won't pick up an edited `src/main/resources/...` file until Gradle recopies it into `build/resources/main/` on the next `bootRun`.

Trigger both pipelines manually (don't wait for the 18:00 IST cron) via the admin-authenticated endpoint:
```
POST /api/v1/pipeline-definitions/{id}/run
```
(Get each pipeline's `id` from `GET /api/v1/pipeline-definitions` — look for `financial-quarterly-results` and `ownership-shareholding-pattern`.)

Verify by querying `financial.financial_results` / `ownership.shareholding_pattern` joined to `reference.instruments` for the new symbols. Then run `./gradlew test` to confirm nothing regressed, and check the actual scoring tables (`technical.technical_scores`, `financial.fundamental_scores`, `ownership.institutional_scores`, `sector.sector_scores`, `risk.risk_scores`) once the evening cron cascade (or a manual invocation) has run — see [claude.md](../claude.md)'s cron schedule for exact times. Corporate Score and Decision Score additionally depend on the Claude-dependent corporate pipeline having a real `ANTHROPIC_API_KEY` configured that day.

## 4. CSV formats (exact column contracts)

### Price history backfill — NSE's raw bhavdata format

Unlike financial results and shareholding, price backfill data is never hand-entered — it's fetched directly from NSE's live archive and written straight into a Flyway migration as `VALUES` rows, not staged as a checked-in CSV. There is a bundled sample at `market/src/main/resources/market-data/sample-bhavdata.csv` (used by the local-profile `BhavdataCollector`), but that's a small fixed single-day snapshot for dev/test, not something to hand-extend for a real backfill.

The raw format returned by `https://archives.nseindia.com/products/content/sec_bhavdata_full_<DDMMYYYY>.csv` (comma-space separated):

```
SYMBOL, SERIES, DATE1, PREV_CLOSE, OPEN_PRICE, HIGH_PRICE, LOW_PRICE, LAST_PRICE, CLOSE_PRICE, AVG_PRICE, TTL_TRD_QNTY, TURNOVER_LACS, NO_OF_TRADES, DELIV_QTY, DELIV_PER
```

Filter to rows matching `^<SYMBOL>, EQ,` (anchored — see §3 Step 3) across every trading day in the target window, then map each matched row into the migration's `market.daily_prices` VALUES tuple:

| Source column (NSE raw) | Target column (`market.daily_prices`) | Notes |
|---|---|---|
| `SYMBOL` | `symbol` (join key, not stored directly) | Resolved to `instrument_id` via `JOIN reference.instruments i ON i.symbol = v.symbol` |
| `DATE1` (`DD-Mon-YYYY`, e.g. `09-Apr-2026`) | `trade_date` | Convert to `YYYY-MM-DD` before writing the migration |
| `OPEN_PRICE` | `open_price` | |
| `HIGH_PRICE` | `high_price` | |
| `LOW_PRICE` | `low_price` | |
| `CLOSE_PRICE` | `close_price` | Use the official close, **not** `LAST_PRICE` |
| `TTL_TRD_QNTY` | `volume` | |
| `DELIV_PER` | `delivery_percentage` | Trim leading/trailing whitespace — NSE pads this field |
| `PREV_CLOSE`, `AVG_PRICE`, `TURNOVER_LACS`, `NO_OF_TRADES`, `DELIV_QTY` | — | Not stored; `market.daily_prices` doesn't have columns for these |

Resulting migration shape (see `V3__backfill_universe_batch1_prices.sql` for the full pattern):

```sql
INSERT INTO market.daily_prices (id, instrument_id, trade_date, open_price, high_price, low_price, close_price, volume, delivery_percentage)
SELECT gen_random_uuid(), i.id, v.trade_date, v.open_price, v.high_price, v.low_price, v.close_price, v.volume, v.delivery_percentage
FROM (VALUES
    ('SYMBOL', DATE '2026-04-09', 3179.90, 3199.90, 3131.00, 3166.80, 3039565, 43.84),
    ...
) AS v(symbol, trade_date, open_price, high_price, low_price, close_price, volume, delivery_percentage)
JOIN reference.instruments i ON i.symbol = v.symbol
ON CONFLICT (instrument_id, trade_date) DO NOTHING;
```

### `financial/src/main/resources/financial-data/sample-financial-results.csv`

```
SYMBOL,PERIOD_END,PERIOD_TYPE,SALES,PAT,EPS,ROE_PCT,ROCE_PCT,OPERATING_MARGIN_PCT,NET_MARGIN_PCT,CASH_FLOW_FROM_OPS,TOTAL_ASSETS,CURRENT_ASSETS,CURRENT_LIABILITIES,TOTAL_DEBT,TOTAL_EQUITY,INTEREST_EXPENSE,EBIT
```

| Column | Required | Notes |
|---|---|---|
| `SYMBOL` | Yes | Must already exist in `reference.instruments` |
| `PERIOD_END` | Yes | `YYYY-MM-DD`, the quarter/year end date |
| `PERIOD_TYPE` | Yes | `QUARTERLY` or `ANNUAL` |
| `SALES`, `PAT` | Yes | ₹ crores. For banks, `SALES` substitutes net interest income (a disclosed, not-yet-sector-aware simplification) |
| Everything else | No | Leave blank rather than guess. Blank is a normal, expected outcome for many fields — the original 8's CSV rows are sparse throughout |

Loader upserts on `(instrument_id, period_end, period_type)` — safe to re-run.

### `ownership/src/main/resources/ownership-data/sample-shareholding.csv`

```
SYMBOL,PERIOD_END,PROMOTER_PCT,FII_PCT,DII_PCT,MF_PCT,PUBLIC_PCT
```

| Column | Required | Notes |
|---|---|---|
| `SYMBOL` | Yes | Must already exist in `reference.instruments` |
| `PERIOD_END` | Yes | `YYYY-MM-DD` — independent of the financial results period, use whatever's actually most recently published |
| `PROMOTER_PCT`, `FII_PCT`, `DII_PCT` | Yes (schema `NOT NULL`) | Must always have a value — if two sources disagree by a small amount, pick one and note the alternate in the commit message, don't leave blank |
| `MF_PCT`, `PUBLIC_PCT` | No | Leave blank if not separately broken out |

Loader upserts on `(instrument_id, period_end)` — safe to re-run.

## 5. What this process cannot do

Documenting this doesn't make it automatic. Re-running this runbook for symbol #21 still needs a human (or agent) to do real research each time — there is no scheduled job that keeps financial/shareholding data current even for the 20 instruments already tracked; both datasets are frozen at whatever quarter was researched until someone repeats Step 4 for a newer period. If AlphaGraph outgrows this manual process, the actual fix is a paid financial-data vendor subscription with a real bulk API — not a smarter script, since the underlying constraint is that no free bulk source for this data exists in India today.
