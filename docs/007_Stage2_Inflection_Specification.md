# 007 — Stage 2 Inflection Specification

## 1. Purpose

Stage 1 (`*.transformation_evidence`, one append-only ledger per domain module) answers "what
changed." Stage 2 answers "does that change mean something" — it reads Stage 1's evidence and
bands it into a fixed vocabulary of interpreted inflection states, each carrying the same five
dimensions, reason codes, and a confidence score.

This document is the complete Stage 2 specification requested before any Stage 2 code is written:
every state, its exact source metric(s), trigger logic, persistence rule, reason codes, and
priority ordering within its family — grounded against the real evidence that exists in the
database today, not assumed. Where real evidence is too thin or a required Stage 1 metric doesn't
exist yet, that is stated plainly rather than guessed around.

Stage 3 (sequence/transformation detection), Stage 4 (cross-domain convergence), and Stage 5
(lifecycle) are explicitly **out of scope** for this document — they consume Stage 2's *output*,
which doesn't exist yet. Each gets its own spec once Stage 2 is real and has enough history to
feed them, the same reasoning the user gave for ordering Risk/Contradiction last within Stage 2
itself.

## 2. Shared state shape

Every Stage 2 state, regardless of family, carries the same six fields:

| Field | Meaning |
|---|---|
| `level` | The current Stage 1 evidence `value` the state is interpreting (pass-through, not recomputed) |
| `change` | The current Stage 1 evidence `change` (pass-through) |
| `velocity` | Banded from Stage 1's numeric `velocity_*` into `STRONG` / `MODERATE` / `WEAK` / `NEGATIVE` (see §3) |
| `persistence` | Count of consecutive same-direction Stage 1 evidence rows for the driving metric, in that metric's own native cadence (quarters for Financial/Ownership, days for Market/Sector) |
| `confidence` | 0-100, see §4 |
| `reason_codes` | Ordered list of the specific sub-conditions that actually fired, same "record every condition that matched even though only one state wins" convention `ownership.transformation.OwnershipTransformationEngine` already uses |

```
REVENUE_ACCELERATION
  level          74
  change         +11.8 pp YoY
  velocity       STRONG
  persistence    3 quarters
  confidence     92%
  reason_codes:  [REVENUE_GROWTH_IMPROVING, YOY_ACCELERATION_3Q]
```

Persisted the same way every Stage 1 family already persists its own state (Ownership's
`transformation_states`/`_reasons` precedent): one **upsert-latest-per-day** row per
(instrument, state family), never a historical ledger — Stage 2 states are current
interpretations, not evidence.

**Storage decision (revised - see §15.3 for why)**: **per-domain tables, not one shared
`intelligence` table.** Checked real schema-ownership precedent before deciding: `intelligence`
has never owned a Postgres schema in this codebase (zero migrations under `intelligence/`) -
every cross-domain compute in `intelligence` persists through a *domain-owned* Writer class
(`sector.transformation.SectorContextEvidenceWriter` being the most recent example), never
directly into a schema of its own. Six of the seven families' Stage 2 states need **no
cross-domain read at all** - by the time Stage 2 runs, each family's own Stage 1 evidence table
already has everything (Sector's own cross-domain work already happened at Stage 1 evidence time).
So each of those six writes `<domain>.inflection_states`/`_reasons`, owned and written by that
domain module, exactly mirroring `ownership.transformation_states`. Only Risk/Contradiction is
genuinely cross-domain (reads every other family's states at once) - it lives in `intelligence`
(computation) and writes to `risk.contradiction_states` (an existing domain module that already
owns its own schema, `risk/V1__init_schema.sql`, and is the correct semantic owner), the same
compute-in-intelligence/write-via-domain-owner split `intelligence.sectorcontext` already
established. A later Stage 3/4 that needs to read across every family's states does the same
raw-SQL-by-value cross-schema read `intelligence.sectorcontext.MarketPriceReturnLookup` and
`market.pricing.DiscoveryCandidateLookup` already prove out - six small reads, not an argument for
centralizing storage.

## 3. Velocity banding

**Corrected during Ownership's implementation (2026-09-18)**: this section originally said
velocity banding "is a `common.rules` `RuleSet`... not hardcoded Java." That was wrong, caught
while implementing it - `OwnershipTransformationEngine`'s own javadoc, one line away from where
this banding lives, already documents the opposite convention for this exact class: "final
categorical banding is never itself a DB rule, matching `DealMaterialityEngine#materialityLevelFor`'s
convention." Velocity bands are exactly that kind of terminal categorical label, same as
`materialityLevelFor`, `ratingFor`, and the `primary_state` priority ladder itself. **As actually
built**: `common.inflection.VelocityBand` (public enum, shared kernel) plus a hardcoded Java
`VelocityBanding` utility per family (`ownership.transformation.VelocityBanding` first, Market and
Sector get their own copies later, matching the `*InstrumentLookup` duplicated-rather-than-shared
convention) - no DB rule, no migration for thresholds.

Stage 1 already computes a numeric velocity (`velocity_pp_per_quarter`, `velocity_per_day`,
`velocity_per_quarter`) but never bands it. One threshold set per metric *type* (percentage-point
change, absolute-currency change, ratio change), not per individual metric, since the bands are
about the magnitude of change relative to the metric's own typical scale:

- Percentage-point metrics (margins, ownership %, relative strength spreads):
  ```
  STRONG     >= 2.0
  MODERATE   >= 0.5
  WEAK       > 0
  FLAT       = 0
  NEGATIVE   < 0
  ```
  Same order of magnitude as the already-seeded `ownership-transformation-*-change` thresholds
  (30-signal-score at 0.6pp). **Amended during Ownership's implementation**: zero velocity is
  `FLAT`, not `NEGATIVE` - "negative velocity" for a metric that simply didn't move would be a
  semantically odd output, and `VelocityBand` is shared kernel other families will reuse.
- Currency metrics (Revenue, PAT, Interest Expense): no fixed absolute threshold makes sense across
  companies of very different size — band on **percentage change**, not the raw currency delta:
  `STRONG` >= 15%, `MODERATE` >= 5%, `WEAK` > 0%, `FLAT` = 0%, `NEGATIVE` < 0%.
- Ratio metrics (Relative Volume): `STRONG` >= 0.5 (i.e. RV moved by half a turn), `MODERATE` >=
  0.2, `WEAK` > 0, `FLAT` = 0, `NEGATIVE` < 0.

**Disclosed, same as every Stage 1 hardcoded categorical boundary**: these are reasonable starting
bands, not yet calibrated against a full real distribution (there isn't one yet - depth varies
wildly by family, §5). Expect to retune once Stage 2 runs against more real data, exactly like
`DealMaterialityEngine`'s own ADTV-boundary precedent already documents.

## 4. Confidence

Two components, matching the pattern Stage 1's own evidence rows already use (a fixed
first-observation confidence vs. a fixed steady-state confidence) but made continuous instead of
two flat constants, since Stage 2 states genuinely vary in how much history backs them:

```
confidence = clamp(base_confidence(driving_metric) + persistence_bonus - thinness_penalty, 0, 100)
```

- `base_confidence`: 60 for a state driven by a metric with a real, disclosed data gap in its own
  family (e.g. anything reading `INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR` today); 75 for a metric
  with real but thin history (Ownership's FII/DII, avg ~3 real transitions); 90 for a metric with
  deep real history (Market's ~83-day averages, Ownership's PROMOTER/PUBLIC at ~19 transitions
  average).
- `persistence_bonus`: `+2 x persistence`, capped at `+10` — more consecutive confirming
  observations should raise confidence, but with a small, capped effect so a single
  long-persistence state doesn't dominate the whole score.
- `thinness_penalty`: `-10`, applied **only when there is no prior observation at all** (the
  metric's own `priorPeriodEnd`/equivalent is null) - **corrected during Ownership's
  implementation**: the original text said "if `persistence == 0`", but a metric can also read
  `persistence == 0` on a genuine fresh reversal (a real prior value exists, the trend just flipped
  direction) - conflating those would unfairly penalize a real reversal the same as a total absence
  of data. Only the true first-observation case gets the penalty.
- **Explicit clamp step**, not left implicit: `clamp(x, 0, 100)` is applied to the final sum, since
  `persistence_bonus`/`thinness_penalty` are unbounded inputs in principle even though today's real
  values keep the raw sum in range.

This is a starting formula, not a final one — same disclosed-not-final
status as the velocity bands above.

## 5. Readiness ledger (checked against real data, 2026-09-18)

| Family | Real Stage 1 depth today | Verdict |
|---|---|---|
| **Ownership** | PROMOTER/PUBLIC: avg 19.3 transitions/instrument (max 30). FII/DII/MF/FPI/Insurance/Other: avg 2.2-4.9 (max 5-6, XBRL-enrichment-gated) | **Already built.** `OwnershipTransformationEngine`'s existing state banding (`FII_ACCUMULATION`, `DII_ACCUMULATION`, `PROMOTER_HOLDING_INCREASE`, `PROMOTER_DILUTION`, `INSTITUTIONAL_OWNERSHIP_EXPANSION`, `BULK_BUYING_WITH_OWNERSHIP_EXPANSION`, plus `OWNERSHIP_CONTRADICTION`) is word-for-word the family's Stage 2 spec below. See §9. |
| **Business** (Revenue) | 4 transitions/symbol, uniform, 50 non-bank symbols | Ready to build. Thin (4 transitions is the ceiling until more real quarters are filed) but real and uniform. |
| **Earnings** (PAT/Margin) | Same as Business | Ready to build, same thinness caveat. |
| **Balance-Sheet** (Interest Expense only) | Same as Business | Ready to build for `INTEREST_COST_DECLINING` only - `DELEVERAGING_INFLECTION`/`CASH_FLOW_IMPROVEMENT` blocked on the same real Stage 1 gap already disclosed (no live `DEBT_LEVEL`/`CASH_FLOW_FROM_OPERATIONS` source). |
| **Market Accumulation** | avg 83 days/instrument, deep | Ready to build, best-supported family. |
| **Capital Allocation** | Logic-ready, **zero real events** (`corporate.corporate_actions` has 10 real rows, all `DIVIDEND`, confirmed live during Tier 4) | Buildable now, but will not fire on anything real until a real `BUYBACK`/`RIGHTS` event is actually ingested. Build it - it costs nothing extra and the real-data gap is upstream, not in Stage 2. |
| **Sector** | `SECTOR_RELATIVE_STRENGTH`: 25 real days. `VS_NIFTY`: 1 real common day (real gap - `NIFTY50` has only 23 days of its own price history and a real 5-day hole against `RELIANCE`'s, confirmed live). `VS_SECTOR`: 0 rows (`reference.sector_benchmarks` is empty) | `SECTOR_STRENGTHENING` ready. `STOCK_OUTPERFORMING_NIFTY` buildable but will rarely have a persistence history to band yet. `STOCK_OUTPERFORMING_SECTOR`/`NEW_LEADERSHIP_EMERGENCE` **blocked** until `reference.sector_benchmarks` has real rows - build the logic, expect it to be silent. |
| **Risk/Contradiction** | Depends on every other family's Stage 2 states existing | Build last, per the user's own ordering - see §13. |

## 6. Business Inflection

Source: `financial.transformation_evidence` (`REVENUE`).

| State | Trigger | Persistence rule | Reason codes |
|---|---|---|---|
| `REVENUE_ACCELERATION` | Walk the last 3 `REVENUE` evidence rows (oldest to newest) by `period_end`; each row's own `change` (not `value`) must be greater than the previous row's `change` - a real second-derivative check, not just "revenue went up" | Count of consecutive rows satisfying that comparison, capped at 3 (the real ceiling - only 4 transitions exist per symbol today) | `REVENUE_GROWTH_IMPROVING` (fires if only the latest transition qualifies); `YOY_ACCELERATION_3Q` (fires only if all 3 available transitions qualify AND at least one used `comparator_used = YOY`, so a pure-QoQ artifact of one seasonal quarter doesn't get the strongest label) |
| `ORDER_INFLOW_ACCELERATION` | **No Stage 1 evidence exists for this today.** `corporate.orderbook` (Module 2.4, real order-value extraction) is a real candidate source but has never been turned into a `financial`-style evidence ledger. **Not buildable from this spec alone** - needs a Stage 1 scoping pass of its own (new evidence family, not Stage 2 work) before this state can exist. | — | — |
| `CAPEX_MONETIZATION_START` | **No Stage 1 evidence exists for this today.** `corporate.commentary`'s `management_observations` (Module 2.5, real `CAPEX` metric-type extractions) is a real candidate source but is a qualitative commitment-level signal, not a numeric evidence ledger with level/change/velocity - turning it into one is a real, separate scoping question (how does "we spent 500cr on capex" become a *rate*?). **Not buildable from this spec alone.** | — | — |
| `BUSINESS_GROWTH_INFLECTION` | Composite: `REVENUE_ACCELERATION` fired AND (once it exists) `ORDER_INFLOW_ACCELERATION` also fired in the same window | Inherits the stricter of its two inputs' persistence | `MULTI_SIGNAL_GROWTH` |

**Priority within family**: `BUSINESS_GROWTH_INFLECTION` > `REVENUE_ACCELERATION` (a composite is
more informative than its own ingredient) - but since the composite needs a metric that doesn't
exist yet, in practice only `REVENUE_ACCELERATION` is live until `ORDER_INFLOW_ACCELERATION` is
built.

## 7. Earnings Inflection

Source: `financial.transformation_evidence` (`PAT`, `OPERATING_MARGIN`, `REVENUE`).

| State | Trigger | Persistence rule | Reason codes |
|---|---|---|---|
| `PAT_ACCELERATION` | Same second-derivative walk as `REVENUE_ACCELERATION`, applied to `PAT` | Same capped-at-3 rule | `PAT_GROWTH_IMPROVING`, `YOY_ACCELERATION_3Q` |
| `STRUCTURAL_MARGIN_EXPANSION` | `OPERATING_MARGIN`'s `change` is positive for >= 3 consecutive evidence rows (a persistence check, not an acceleration check - "structural" means sustained, not necessarily accelerating) | Count of consecutive positive-`change` rows, capped at 3 | `MARGIN_EXPANDING_SUSTAINED` |
| `OPERATING_LEVERAGE_INFLECTION` | Cross-metric, same window: `REVENUE`'s % growth < `OPERATING_MARGIN`-implied EBITDA's % growth < `PAT`'s % growth, all three positive, on the same `period_end` transition. Needs all three metrics present for that instrument - correctly silent for the 5 real bank symbols, which only ever get `PAT` (§ from Tier 2's own disclosed bank/non-bank field split) | Not itself persistence-gated (it's a single-quarter structural read); `MULTI_QUARTER_EARNINGS_ACCELERATION` below is the persistence-gated version | `REVENUE_TO_EBITDA_LEVERAGE`, `EBITDA_TO_PAT_LEVERAGE` |
| `MULTI_QUARTER_EARNINGS_ACCELERATION` | `PAT_ACCELERATION` AND `STRUCTURAL_MARGIN_EXPANSION` AND `REVENUE_ACCELERATION` (from §6) all fired in the same window - this is the spec's own named composite ("Revenue + PAT + margin improving together"), and per the user's explicit correction this name is reserved for the combined read, never used for revenue alone | Inherits the minimum persistence across its three inputs | `COMBINED_GROWTH_MARGIN_PAT` |

**Priority within family**: `MULTI_QUARTER_EARNINGS_ACCELERATION` > `OPERATING_LEVERAGE_INFLECTION`
> `PAT_ACCELERATION` / `STRUCTURAL_MARGIN_EXPANSION` (the two base states can co-fire and both get
recorded as reason codes even when the composite wins, same non-hiding convention Ownership's
ladder already uses).

## 8. Balance-Sheet Inflection

Source: `financial.transformation_evidence` (`INTEREST_EXPENSE` only - real Tier 1 scope limit).

| State | Trigger | Persistence rule | Reason codes |
|---|---|---|---|
| `INTEREST_COST_DECLINING` | `INTEREST_EXPENSE`'s `change` is negative (interest expense falling) for >= 2 consecutive evidence rows | Count of consecutive negative-`change` rows, capped at 3 | `INTEREST_EXPENSE_FALLING` |
| `DELEVERAGING_INFLECTION` | **Blocked.** Needs real `DEBT_LEVEL` evidence, which needs a real live source for `financial.financial_results.total_debt` - the same gap already disclosed when Balance-Sheet was originally scoped (Tier 3). | — | — |
| `CASH_FLOW_IMPROVEMENT` | **Blocked.** Same reasoning, `cash_flow_from_operations`. | — | — |

No composite state for this family - `INTEREST_COST_DECLINING` alone doesn't imply enough to
combine with anything yet, and the two states that would make a real "balance sheet strengthening"
composite meaningful are both blocked.

## 9. Ownership Inflection

**Already built** - `ownership.transformation.OwnershipTransformationEngine` (live since the first
Multibagger Discovery vertical slice) already implements exactly this family's Stage 2, under the
same names the new spec asks for:

| Spec name | Existing implementation |
|---|---|
| `FII_ACCUMULATION` | `TransformationState.FII_ACCUMULATION` |
| `DII_ACCUMULATION` | `TransformationState.DII_ACCUMULATION` |
| `PROMOTER_HOLDING_INCREASE` | `TransformationState.PROMOTER_HOLDING_INCREASE` |
| `PROMOTER_DILUTION` | `TransformationState.PROMOTER_DILUTION` |
| `INSTITUTIONAL_OWNERSHIP_EXPANSION` | `TransformationState.INSTITUTIONAL_OWNERSHIP_EXPANSION` |
| `BULK_BUYING_WITH_OWNERSHIP_EXPANSION` | `TransformationState.BULK_BUYING_WITH_OWNERSHIP_EXPANSION` |

Persisted in `ownership.transformation_states`/`_reasons`, banded via a real seeded `RuleSet`
(`ownership-transformation-*-change`, `common` V15), with a hardcoded Java priority ladder
(`OwnershipTransformationEngine.PRIORITY_LADDER`) - the exact shared shape §2-4 of this document
generalizes from. `OWNERSHIP_CONTRADICTION` already exists too and is reused directly by
Risk/Contradiction (§13) rather than redefined.

**Retrofit completed (2026-09-18)**: the five-field shape now lands on `transformation_states`
verbatim (`V16` migration added `driving_metric`/`level`/`change`/`velocity_band`/`persistence`
columns - they didn't exist at all before this retrofit, a bigger gap than originally scoped, since
the four dimensions only ever lived on the per-metric `EvidenceObservation`s, never attached to the
one winning state). Each field passes through from whichever metric actually *drove* the winning
state - selected by **participation**, not raw magnitude: for `INSTITUTIONAL_OWNERSHIP_EXPANSION`/
`BULK_BUYING_WITH_OWNERSHIP_EXPANSION` the larger of the two (both guaranteed positive, since both
FII and DII must have signaled to fire either state); for `OWNERSHIP_CONTRADICTION`, whichever of
FII/DII actually signaled (if only one did, it wins outright regardless of the other's raw
magnitude - picking by magnitude alone could otherwise attach the contradiction's dimensions to a
metric that never actually participated). `NO_CLEAR_SIGNAL` gets no driving metric and keeps the
engine's original `averageConfidence(PROMOTER, FII, DII)` fallback; every other state uses the §4
formula, `base_confidence` 90 for `PROMOTER`/`PUBLIC`, 75 for the other 7 (§15.5). Live-verified:
COALINDIA's real `OWNERSHIP_CONTRADICTION` row (FII +1.99pp, the same real figure confirmed during
this family's original live verification) landed with `driving_metric=FII`, `velocity_band=MODERATE`,
`persistence=3`, `confidence=81` (`75 + min(10, 2x3) = 81`, hand-verified exact).

## 10. Market Accumulation Inflection

Source: `market.transformation_evidence` (`RELATIVE_VOLUME`, `DELIVERY_PERCENTAGE_20D_AVG`,
`PRICE_RETURN_20D`) - the best-supported family, ~83 real days deep on average.

| State | Trigger | Persistence rule | Reason codes |
|---|---|---|---|
| `DELIVERY_EXPANSION` | `DELIVERY_PERCENTAGE_20D_AVG`'s `change` positive | Consecutive positive-`change` days, capped at 10 (10 trading days ~ 2 weeks, a real, meaningful window given ~83 real days of depth to check against, unlike the thinner families capped at 3) | `DELIVERY_RISING` |
| `RELATIVE_VOLUME_EXPANSION` | `RELATIVE_VOLUME`'s `change` positive AND current `value` > 1.0 (genuinely above its own 20-day average, not just "less negative") | Same capped-at-10 rule | `RELATIVE_VOLUME_RISING`, `ABOVE_AVERAGE_VOLUME` |
| `SUSTAINED_DELIVERY_ACCUMULATION` | `DELIVERY_EXPANSION` persistence >= 5 | Inherits `DELIVERY_EXPANSION`'s own persistence | `DELIVERY_EXPANSION_SUSTAINED_5D` |
| `STEALTH_ACCUMULATION_CANDIDATE` | `DELIVERY_EXPANSION` AND `RELATIVE_VOLUME_EXPANSION` both fired, AND `PRICE_RETURN_20D`'s current `value` is within +/-3% (accumulation happening without the price yet giving it away - the whole point of "stealth") | Inherits the minimum of its two inputs' persistence | `VOLUME_DELIVERY_UP_PRICE_FLAT` |
| `EARLY_PRICE_PARTICIPATION` | `STEALTH_ACCUMULATION_CANDIDATE` was true as of the *prior* evidence row, and `PRICE_RETURN_20D`'s `change` just turned positive and > 3% this row - the breakout moment right after stealth accumulation ends | Not persistence-gated - it is itself a transition-detection state (a one-time "just crossed" event, re-evaluated fresh each day) | `BREAKOUT_FROM_STEALTH_ACCUMULATION` |

**Priority within family**: `EARLY_PRICE_PARTICIPATION` > `STEALTH_ACCUMULATION_CANDIDATE` >
`SUSTAINED_DELIVERY_ACCUMULATION` > `DELIVERY_EXPANSION` / `RELATIVE_VOLUME_EXPANSION` (later
stages of the same real pattern take priority, earlier ones still recorded as reason codes).

## 11. Capital Allocation Inflection

Source: `corporate.transformation_evidence` (`BUYBACK_EVENT_COUNT_180D`,
`EQUITY_RAISE_EVENT_COUNT_180D`). **Zero real events exist in the tracked universe today** (§5) -
these states are correct to build now (near-zero marginal cost, same reasoning that shipped
Capital Allocation's Stage 1 tier even with an empty real dataset) but will not produce a single
real non-empty result until a real `BUYBACK`/`RIGHTS` action is actually ingested for a tracked
instrument.

| State | Trigger | Persistence rule | Reason codes |
|---|---|---|---|
| `BUYBACK_ACTIVITY` | `BUYBACK_EVENT_COUNT_180D`'s current `value` > 0 | Not persistence-gated in the usual sense - `persistence_days` here is inherited directly from Stage 1's own rolling-window persistence (already computed, see Tier 4's `CapitalAllocationEngine`) | `REAL_BUYBACK_IN_WINDOW` |
| `EQUITY_RAISE_ACTIVITY` | `EQUITY_RAISE_EVENT_COUNT_180D`'s current `value` > 0 | Same | `REAL_RIGHTS_ISSUE_IN_WINDOW` |
| `INSTITUTIONAL_CAPITAL_RAISE` | **Blocked.** Needs QIP/preferential-allotment/warrant classification, which needs new `action_type` values in `corporate.corporate_actions`' CHECK constraint plus classifier keyword rules - the exact disclosed schema gap from Tier 4's own scoping. | — | — |
| `DILUTION_PRESSURE` | **Blocked.** Same reasoning - a real dilution-pressure read needs pricing/size/take-up context this schema doesn't capture yet, the same reason `EQUITY_RAISE_EVENT_COUNT_180D` was deliberately named neutrally instead of as a dilution metric in Tier 4. | — | — |

No composite/priority ladder needed yet - `BUYBACK_ACTIVITY` and `EQUITY_RAISE_ACTIVITY` are
independent and both simple pass-throughs of an already-real Stage 1 count.

## 12. Sector Inflection

Source: `sector.transformation_evidence` (`SECTOR_RELATIVE_STRENGTH`,
`INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY`, `INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR`).

| State | Trigger | Persistence rule | Reason codes |
|---|---|---|---|
| `SECTOR_STRENGTHENING` | `SECTOR_RELATIVE_STRENGTH`'s `change` positive | Consecutive positive-`change` days, capped at 5 (25 real days of depth exist, but a sector-level read shouldn't claim more persistence confidence than the instrument-level states get) | `SECTOR_RS_RISING` |
| `STOCK_OUTPERFORMING_NIFTY` | `INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY`'s current `value` > 0 | Real gap (§5): only 1 common real day exists between most instruments and `NIFTY50` today, so `persistence` will almost always read 0 until `NIFTY50`'s own price history and/or the stock-level gap fills in over time - disclosed, not hidden by a fake bonus | `OUTPERFORMING_NIFTY_20D` |
| `STOCK_OUTPERFORMING_SECTOR` | `INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR`'s current `value` > 0 | **Blocked** - zero real rows exist until `reference.sector_benchmarks` has real mappings | `OUTPERFORMING_SECTOR_20D` |
| `NEW_LEADERSHIP_EMERGENCE` | `STOCK_OUTPERFORMING_SECTOR` was false as of the prior evidence row and true this row (a fresh crossing, not a standing condition) | Not persistence-gated - transition-detection, same shape as `EARLY_PRICE_PARTICIPATION` | `SECTOR_LEADERSHIP_CROSSING` |

**Priority within family**: `NEW_LEADERSHIP_EMERGENCE` > `STOCK_OUTPERFORMING_SECTOR` >
`STOCK_OUTPERFORMING_NIFTY` > `SECTOR_STRENGTHENING` (a stock-specific read outranks the
sector-wide read it's built from).

## 13. Risk / Contradiction (last, by design)

Source: **other Stage 2 states**, not Stage 1 evidence directly - this family is a meta-layer, per
the user's own explicit ordering. Every trigger below assumes the referenced states from §6-12
already exist and are queryable.

| State | Trigger | Reason codes |
|---|---|---|
| `GROWTH_QUALITY_CONTRADICTION` | `REVENUE_ACCELERATION` (§6) fired AND `STRUCTURAL_MARGIN_EXPANSION` (§7) did *not* fire AND `OPERATING_MARGIN`'s own `change` is negative - revenue growing on shrinking margins, a real quality flag | `REVENUE_UP_MARGIN_DOWN` |
| `OWNERSHIP_CONTRADICTION` | **Already exists** - reused directly from §9, not redefined | (inherited from Ownership's own reason codes) |
| `PRICE_WITHOUT_DELIVERY_CONFIRMATION` | `PRICE_RETURN_20D`'s `change` is strongly positive (crosses the `STRONG` band, §3) but `DELIVERY_EXPANSION` (§10) did *not* fire in the same window - a price move institutions/genuine holders aren't backing with delivery | `PRICE_UP_DELIVERY_FLAT_OR_DOWN` |
| `CAPITAL_RAISE_WITH_WEAK_BUSINESS_INFLECTION` | `EQUITY_RAISE_ACTIVITY` (§11) fired AND neither `REVENUE_ACCELERATION` (§6) nor `PAT_ACCELERATION` (§7) fired for the same instrument in the same window - raising capital without a real growth signal backing it | `EQUITY_RAISE_NO_GROWTH_SIGNAL` |
| `MULTI_DOMAIN_CONTRADICTION` | >= 2 of the above four contradiction states fired simultaneously for the same instrument | `MULTIPLE_CONTRADICTIONS` plus every contributing state's own reason codes |

No priority ladder within this family needed at the interpretation layer - unlike the other
families, multiple contradictions co-existing is itself the signal (`MULTI_DOMAIN_CONTRADICTION`),
so all firing contradiction states should be recorded, not collapsed to one winner.

## 14. Recommended build order

Mirrors Stage 1's own "build in order of real readiness, not spec order" discipline:

1. **Ownership** - already built; retrofit the velocity-banding shape (§3) to match this spec, no
   new engine design.
2. **Market Accumulation** - deepest real history, zero blocked states, the cleanest new build.
3. **Business + Earnings** - real but thin (4 transitions/symbol ceiling); build both together since
   they share the acceleration-walk logic and the `MULTI_QUARTER_EARNINGS_ACCELERATION` composite
   spans both.
4. **Balance-Sheet** (`INTEREST_COST_DECLINING` only) + **Capital Allocation** - small, cheap,
   logic-complete even though one will be silent until real non-dividend corporate actions appear.
5. **Sector** - build all four states, but disclose upfront that `STOCK_OUTPERFORMING_SECTOR` and
   `NEW_LEADERSHIP_EMERGENCE` will stay silent until `reference.sector_benchmarks` is populated.
6. **Risk/Contradiction** - last, once 1-5 are real and queryable, per the user's own ordering.

`ORDER_INFLOW_ACCELERATION` and `CAPEX_MONETIZATION_START` (§6) are not in this build order at
all - they need a new Stage 1 evidence family of their own (Order Book / Management Commentary
turned into numeric ledgers) before any Stage 2 state can be built on top of them. That is a
separate scoping exercise, not part of Stage 2 implementation.

## 15. Implementation gap resolutions

Seven real implementation gaps were identified after this spec's first draft (asked explicitly:
"any gaps in implementing stage 2?"). Resolved here, concretely, before any Stage 2 code is
written - each subsection is numbered to match the original gap list.

### 15.1 Evidence readers don't exist yet

Every Stage 1 `*TransformationEvidenceWriter` across all five tiers is write-only - confirmed
during Stage 1 implementation, never revisited until now. Two different reader needs, two
different visibilities:

- **Reading a family's own Stage 1 evidence** (needed by that family's own Stage 2 engine only):
  package-private reader in the same package as the existing writer, returning the family's own
  observation-shaped record (`MarketEvidenceObservation`, `FinancialEvidenceObservation`,
  `EvidenceObservation`, `SectorContextEvidenceObservation`, `CapitalAllocationEvidenceObservation`
  - all already exist, reused as-is, no new record types). One method:
  `List<X> findRecent(UUID instrumentId, <Metric> metric, int limit)`, ascending by the family's
  own natural date column - `market.transformation.MarketTransformationEvidenceReader`,
  `financial.transformation.FinancialTransformationEvidenceReader`,
  `ownership.transformation.TransformationEvidenceReader` (new),
  `sector.transformation.SectorContextEvidenceReader` (new),
  `corporate.transformation.CapitalAllocationEvidenceReader` (new).
- **Reading a family's Stage 2 *state* table** (needed only by Risk/Contradiction, cross-domain):
  **public** reader per family, e.g. `market.inflection.MarketInflectionStateReader`,
  mirroring `sector.engine.SectorScoreReader`'s existing public visibility for exactly this reason
  (a different module needs to call it). Six of these; Risk/Contradiction's own
  `intelligence.riskcontradiction.RiskContradictionOrchestrator` calls all six.

### 15.2 Self-referential "read own prior state" pattern

`EARLY_PRICE_PARTICIPATION` (§10) and `NEW_LEADERSHIP_EMERGENCE` (§12) both need "was the
condition different as of the last time this ran" - architecturally new, since every engine built
so far (Stage 1, and Ownership's existing Stage 2) derives "prior" from independent source data,
never from its own last output.

**Resolution**: read-before-write, same request, against the same upsert-latest-per-day state
table being written. The orchestrator fetches the existing row for (instrument) from its own
`<domain>.inflection_states` table *before* computing today's new state, uses that old row's
relevant boolean condition (e.g. "was `STEALTH_ACCUMULATION_CANDIDATE` the primary state
yesterday") to decide whether today's transition state fires, then upserts the new row over it.
"Prior" means "whatever was last upserted, regardless of how many calendar days ago that was" -
the same date-gap tolerance Stage 1's own persistence/change calculations already have (they
compare against the previous *row* in a series, not literally "yesterday"). First-ever run for an
instrument: no existing row, the transition condition is vacuously false (can't detect a crossing
without two points) - same honest "first observation" handling used everywhere else in Stage 1,
not an error case needing special code.

### 15.3 Storage decision

Resolved directly in §2 above (per-domain tables; Risk/Contradiction writes to `risk`'s own
schema via the `intelligence`-computes/`risk`-writes split).

### 15.4 Metric -> band-type mapping

A `MetricBandType` enum (`PERCENTAGE_POINT`, `CURRENCY_PCT_CHANGE`, `RATIO`, `COUNT`) with one
seeded `RuleSet` per type (§3), and an explicit per-metric mapping table each family's engine
consults (a plain `switch`, not a DB table - this mapping is structural, not tunable, unlike the
thresholds themselves):

| Metric | Type |
|---|---|
| `REVENUE`, `PAT`, `INTEREST_EXPENSE` | `CURRENCY_PCT_CHANGE` |
| `OPERATING_MARGIN` | `PERCENTAGE_POINT` |
| `RELATIVE_VOLUME` | `RATIO` |
| `DELIVERY_PERCENTAGE_20D_AVG`, `PRICE_RETURN_20D` | `PERCENTAGE_POINT` |
| All 9 Ownership shareholding metrics | `PERCENTAGE_POINT` (unchanged - already banded via the existing seeded `RuleSet`, §9) |
| `SECTOR_RELATIVE_STRENGTH`, `INSTRUMENT_RELATIVE_STRENGTH_VS_NIFTY`, `INSTRUMENT_RELATIVE_STRENGTH_VS_SECTOR` | `PERCENTAGE_POINT` |
| `BUYBACK_EVENT_COUNT_180D`, `EQUITY_RAISE_EVENT_COUNT_180D` | `COUNT` - no velocity banding at all; §11's states are direct value>0 pass-throughs |

### 15.5 Confidence bucket assignment

Exhaustive `base_confidence` per §4's three-bucket rule, so no state's bucket is decided ad hoc
while coding:

| Bucket | States |
|---|---|
| 90 (deep real history) | All 5 Market states (§10); Ownership `PROMOTER_HOLDING_INCREASE`/`PROMOTER_DILUTION` (§9); `SECTOR_STRENGTHENING` (§12) |
| 75 (real but thin, or real-but-not-yet-observed) | All Business/Earnings/Balance-Sheet states (§6-8, 4-transition ceiling); Ownership `FII_ACCUMULATION`/`DII_ACCUMULATION`/`INSTITUTIONAL_OWNERSHIP_EXPANSION`/`BULK_BUYING_WITH_OWNERSHIP_EXPANSION` (§9, XBRL-gated); both Capital Allocation states (§11 - the data source isn't gapped, it's just empty right now, a different case from a real disclosed limitation) |
| 60 (real, disclosed data gap) | `STOCK_OUTPERFORMING_NIFTY`, `STOCK_OUTPERFORMING_SECTOR`, `NEW_LEADERSHIP_EMERGENCE` (§12) |
| n/a - derived | Risk/Contradiction (§13): `base_confidence` = the **minimum** `base_confidence` across its contributing states, never its own fixed bucket - a contradiction is only as trustworthy as its weakest input. |

### 15.6 Scheduling order

Every Stage 1 job this depends on completes by 19:10 IST (`ownership-transformation`, the latest).
New slots, all after that, spaced to leave room, none colliding with the existing `JOB_SCHEDULES`
map (`news-catalyst` is the next real neighbor at 19:30):

| Job | Time | Depends on |
|---|---|---|
| `market-inflection` | 19:20 | `market-accumulation-evidence` (18:32) |
| `financial-inflection` (Business + Earnings + Balance-Sheet together - one engine, one source table) | 19:22 | `financial-results-comparision-fetch` (18:37) |
| `ownership-transformation` (retrofit, no new slot) | 19:10 (existing) | unchanged |
| `capital-allocation-inflection` | 19:24 | `capital-allocation-evidence` (18:02) |
| `sector-inflection` | 19:26 | `sector-context-evidence` (18:57) |
| `risk-contradiction-inflection` | 19:29 | all of the above |

### 15.7 "Nothing fired" convention

**Resolved as: always write exactly one row per instrument per family per day**, defaulting to an
explicit `NO_CLEAR_SIGNAL` state when nothing else wins the family's priority ladder - adopting
Ownership's own existing convention (`TransformationState.NO_CLEAR_SIGNAL`) uniformly across all
six primary-state families, rather than silently writing nothing (Stage 1's own convention for
*evidence*, which is correct there since an unavailable metric truly has nothing to report, but
wrong for Stage 2 *states*, where a downstream consumer needs to tell "checked, no signal" apart
from "didn't run today"). **Deliberate exception**: Risk/Contradiction does not write a
`NO_CONTRADICTION` filler row - a contradiction is inherently a multi-state, occasional read, and
"not currently contradictory" is a different kind of claim than the other families' single primary
state; its absence of a row is itself already interpretable as "instrument was checked, nothing
about it contradicted" once Risk/Contradiction's job status is itself observable via
`scheduler.job_runs`, same as every other job's silence-means-nothing-to-report convention.
