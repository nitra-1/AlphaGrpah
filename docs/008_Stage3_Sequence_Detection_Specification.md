# Stage 3 — Sequence / Transformation Detection Specification

Living spec, git-committed, revised after every family's implementation - same convention
`docs/007_Stage2_Inflection_Specification.md` already established for Stage 2. Written before any
Stage 3 code exists; verified against the real, current codebase throughout (state names, table
shapes, migration numbers, the rules mechanism) rather than assumed.

## 1. Objective and stage boundaries

Stage 1 answers "what does the raw evidence say" (value/change/velocity/persistence/confidence).
Stage 2 answers "is an individual metric undergoing an inflection" (one banded state per family per
day, e.g. `REVENUE_ACCELERATION`, `SUSTAINED_DELIVERY_ACCUMULATION`). **Stage 3 answers "are
several related inflections forming a meaningful transformation pattern over time"** - it converts
Stage 2 inflection *histories* into ordered sequences.

```
STAGE 1 (evidence) -> STAGE 2 (individual inflections) -> STAGE 3 (transformation sequences)
    -> STAGE 4 (cross-domain convergence) -> STAGE 5 (lifecycle classification)
```

Stage 3 must **not** implement: a Multibagger Discovery score, cross-domain convergence, lifecycle
classification, forward-return prediction, ML, or a BUY/SELL recommendation. Those are Stage 4/5.
Once Stage 3 exists, a company may show several simultaneous COMPLETE/PROGRESSING sequences across
domains - the temptation to immediately collapse that into one score must be resisted; that
collapsing is Stage 4's entire job, not Stage 3's.

## 2. Core concept: a sequence

A transformation sequence is defined by `sequence_type`, its required/optional steps, their
ordering, a maximum gap between steps, and - per instrument, per `as_of_date` - a `current_step`,
`total_steps`, `first_step_date`, `last_step_date`, `sequence_phase`, `sequence_strength`,
`confidence`, and reasons. The engine must understand not merely *whether* all steps occurred, but
whether they occurred in a temporally reasonable order.

## 3. Sequence phases (identical taxonomy for every domain)

`FORMING` (first meaningful prerequisite detected) -> `PROGRESSING` (>= 2 required steps occurred
in correct order) -> `COMPLETE` (all mandatory steps occurred within the configured window) ->
`BROKEN` (the sequence previously progressed but a material contradictory state, or expiry,
invalidated continuation).

**`COMPLETE` means the evidence sequence completed - it does not mean the investment thesis
succeeded.** Do not use `CONFIRMED`/`FAILED` - this codebase already uses those concepts elsewhere
for forward outcome tracking (`learning.outcomes`), and reusing them here would collide with an
unrelated, already-shipped meaning. Expiry (§13) is represented as `BROKEN` with reason
`SEQUENCE_EXPIRED`, not a 5th phase - keeps the taxonomy small and every consumer only ever has to
handle 4 values.

## 4. Historical-state semantics

Stage 3 reads Stage 2 **history**, never just today's latest state - order is the entire point.
Every domain needs an ascending-by-`as_of_date` reader:

```java
List<InflectionStateHistory> findHistory(UUID instrumentId, LocalDate startDate, LocalDate endDate);
```

**A real, live blocker for Ownership specifically, confirmed by reading the current source**:
`ownership.transformation.OwnershipTransformationEngine.bandStates(...)` still builds its result
with `LocalDate.now()` as `as_of_date` (line 198), not `current.periodEnd()`. This is the exact
cadence bug flagged during this session's Financial Stage 2 work and spun off as a background task
(`task_b3c0547c`, "Fix Ownership Stage 2's `as_of_date` to use `period_end`") - explicitly not fixed
at the time because it was out of scope for Financial. It is squarely in scope now: as long as it's
unfixed, `ownership.transformation_states`' history is "one row per calendar day the job ran," not
"one row per real quarter transition" - `findHistory(...)` would return many redundant same-state
rows for one real quarter, corrupting exactly the ordering/gap logic Stage 3 needs. **Recommendation:
fix this bug as a prerequisite of (or the very first task within) Ownership Stage 3 work, not
something Stage 3 should work around.** Market/Financial/Corporate/Sector's own Stage 2
`as_of_date`s are already evidence-derived (confirmed throughout this session's Stage 2 build) and
don't have this problem.

## 5. Same-day rerun rule

All historical comparisons use `as_of_date`, never `computed_at` - a same-day retry is not another
sequence step. `UNIQUE (instrument_id, as_of_date, sequence_type)` on every sequence table; a
repeated run on the same day updates/recomputes that day's row idempotently (upsert, same
convention every Stage 2 writer already uses), never inserts a second row.

## 6. Sequence timing is cadence-specific, not one generic `daysBetween()`

- **Daily families** (Market, Sector) - gaps measured in **trading sessions**, not calendar days.
- **Quarterly families** (Financial - which already covers Business/Earnings/Balance-Sheet in one
  engine, per Stage 2's own folding decision; Ownership) - gaps measured in **reporting
  periods**/quarter transitions.
- **Event families** (Capital Allocation) - gaps measured in **event dates** within Stage 1's own
  rolling window.

Do not convert everything to one generic day-count algorithm - a "20-day gap" means something
different for a daily market metric than for a quarterly financial one.

## 7. Rule configuration - verified against the real `common.rule_definitions` mechanism

`common.rule_definitions`/`common.rule_conditions` (`common/src/main/resources/db/migration/common/V1__init_schema.sql`)
is real and already seeded for Ownership/Risk/Technical/Fundamental/Sector/Order-Book/Management-
Commentary/News-Catalyst/Corporate-Signal/Decision-Scoring/Deal-Materiality (`V3`-`V15`). Its real
shape: `rule_definitions(name, target_metric, version, active)` + `rule_conditions(rule_id,
operator IN ('GT','LT','GTE','LTE','EQ','BETWEEN','ALWAYS'), threshold, weight)`, evaluated by
`ArithmeticRuleEvaluator`/`WeightedAverageRuleEvaluator` against **one** numeric metric to produce a
weighted score - it is a threshold-crossing scoring ladder, not a generic named-constant store.

**Confirmed workable, not a perfect fit, disclosed**: a single named Stage 3 threshold (e.g.
`stage3-market-recognition-max-gap`) maps onto this mechanism as a **one-condition rule** -
`target_metric` describing what's being measured (e.g. `sessionsSinceStealth`), one `ALWAYS`
condition carrying the threshold as its `weight` (the number Java reads back), following the same
"exhaustive, deliberately simple ladder" convention `V15`'s ownership rules already establish. This
is a real, if novel, use of existing infrastructure (never previously used purely as a scalar
constant), and is what "numeric thresholds -> rules/configuration, categorical output -> Java"
concretely means for Stage 3. Recommended names, one per domain's max-gap/min-persistence concept
(exact values are starting points, to be revisited once real data accumulates, same discipline
`V15`'s own thresholds are already disclosed as needing):

- `stage3-market-recognition-max-gap` (Market, sessions)
- `stage3-stealth-formation-max-window` (Market, sessions)
- `stage3-ownership-building-min-persistence` (Ownership, reporting periods)
- `stage3-ownership-contradiction-tolerance` (Ownership, reporting periods before BROKEN)
- `stage3-earnings-cycle-max-quarter-gap` (Financial, reporting periods)
- `stage3-multi-quarter-earnings-min-persistence` (Financial, reporting periods)
- `stage3-sector-tailwind-max-gap` (Sector, sessions)
- `stage3-leadership-emergence-max-gap` (Sector, sessions)
- `stage3-capital-return-repeat-window` (Corporate, days, matching Stage 1's own 180-day window)

Terminal sequence labels and phase precedence stay hardcoded Java enums, matching every Stage 2
family's own "categorical output is never a DB rule" convention.

## 8. Persistence shape

No single cross-domain table - module ownership stays exactly as Stage 1/2 already established.
Confirmed next available migration number per module (2026-09-21; re-verify before implementing,
per the general discipline of never assuming a version number from a spec document written before
the fact): `financial` V7, `ownership` V17, `market` V7, `corporate` V17, `sector` V4. (`risk` V3
is available too, held in reserve per §12 below - not used by this initial catalogue.)

```sql
CREATE TABLE <schema>.transformation_sequences (
    id                uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    instrument_id     uuid NOT NULL,
    symbol            text NOT NULL,
    as_of_date        date NOT NULL,
    sequence_type     varchar(60) NOT NULL,
    sequence_phase    varchar(20) NOT NULL,  -- FORMING | PROGRESSING | COMPLETE | BROKEN
    sequence_readiness varchar(30) NOT NULL, -- READY | INSUFFICIENT_HISTORY | MISSING_PREREQUISITE_DATA
    current_step      integer NOT NULL,
    total_steps       integer NOT NULL,
    first_step_date   date NULL,
    last_step_date    date NULL,
    sequence_strength numeric(5, 2) NULL,
    confidence        numeric(5, 2) NOT NULL,
    age_periods       integer NOT NULL DEFAULT 0,
    rule_version      integer NOT NULL,
    computed_at       timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT ux_<schema>_transformation_sequences_instrument_date_type
        UNIQUE (instrument_id, as_of_date, sequence_type)
);

CREATE TABLE <schema>.transformation_sequence_reasons (
    id                 uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    sequence_id        uuid NOT NULL REFERENCES <schema>.transformation_sequences (id) ON DELETE CASCADE,
    reason_code        varchar(60) NOT NULL,
    step_number        integer NULL,
    evidence_date      date NULL,
    metric_value       numeric(18, 4) NULL,
    evidence_reference text NULL
);
```

Upsert parent, delete/reinsert reasons - identical convention every Stage 2 writer already uses.
**One row per `(instrument, as_of_date, sequence_type)`, never one row per instrument** - a stock
can legitimately have multiple simultaneous sequences (e.g. `BUSINESS_ACCELERATION_CYCLE` and
`OPERATING_LEVERAGE_CYCLE` both active), and Stage 3 must report every one of them faithfully, not
choose a "primary" or "best" sequence - that ranking is Stage 4's job, not Stage 3's.

## 9. Why keep historical sequence rows

Stage 4/5 will need to know when a sequence started, how fast it progressed, whether/when it broke,
and how long it stayed active - `FORMING` (Jul 1) -> `PROGRESSING` (Jul 18) -> `COMPLETE` (Aug 5) is
far more useful downstream than `COMPLETE` alone. Never prune to latest-only.

## 10. Sequence strength vs. confidence - kept separate

**Confidence** answers "how reliable is the evidence backing this classification" - evidence-
weighted average of the *completed* steps' own real Stage 2 confidence values (later/more-important
steps weighted more heavily - e.g. a 3-step sequence at 20/30/50%, a 2-step one at 40/60%), then a
data-thinness/stale-evidence penalty, clamped to `[0, 100]`. **Never increase confidence merely
because sequence strength is high** - the two measure different things.

**Sequence strength** answers "how much of the expected pattern has actually occurred" - suggested
starting weights: mandatory-step completion 70%, step persistence 20%, optional supporting steps
10%. Never includes price outcome or forward performance (that's Phase 4/5's forward-tracking
concern, already served by `learning.outcomes`, not Stage 3's). Suggested rough bands for a 3-step
sequence: 1 step ~25-35, 2 steps ~55-75, 3 steps 80-100 - exact boundaries rule-driven (§7), not
hardcoded assumptions to be taken literally.

## 11. Evidence freshness / expiry

Every sequence type defines a `max_step_gap` and `max_sequence_age` (both rule-configured, §7). If
the next expected step doesn't arrive before expiry, the sequence becomes `BROKEN` with reason
`SEQUENCE_EXPIRED` - not a 5th phase (§3).

## 12. Readiness - distinct from phase, same lesson as Sector's Stage 2 `SectorDataReadiness`

`sequence_readiness`: `READY`, `INSUFFICIENT_HISTORY`, `MISSING_PREREQUISITE_DATA`. **Insufficient
history must never be interpreted as "no sequence"** - one available financial quarter should
produce `INSUFFICIENT_HISTORY`, not a false "no operating leverage cycle" conclusion. This is the
same principle Sector's Stage 2 `evidence_coverage_pct`/`data_readiness` already established
(`READY` vs `PARTIAL_DATA` vs `INSUFFICIENT_DATA`, confirmed live during that build - most Sector
rows read `PARTIAL_DATA` today because `VS_SECTOR` has zero real rows, not because nothing's
happening) - `READY` + no qualifying steps genuinely means "no active sequence right now,"
`INSUFFICIENT_HISTORY` means "unknown." Stage 4 will need this distinction to avoid a false-negative
reading of thin data as a real absence of transformation.

## 13. Result record shape (conceptual - actual enums stay module-local per architectural boundary)

```java
record SequenceResult(
    UUID instrumentId, String symbol, LocalDate asOfDate,
    SequenceType sequenceType, SequencePhase sequencePhase, SequenceReadiness readiness,
    int currentStep, int totalSteps, LocalDate firstStepDate, LocalDate lastStepDate,
    BigDecimal sequenceStrength, double confidence, int agePeriods, int ruleVersion,
    List<ReasonCode> reasons
)
```

## 14. Domain catalogue - 15 sequences, locked for the first pass

Verified against every real Stage 2 state name currently in the codebase (all confirmed present,
exact spelling, 2026-09-21) - nothing below references a state that doesn't exist.

### 14.1 Financial (`financial.transformation` - covers Business/Earnings/Balance-Sheet, since Stage
2 already folded all three into one engine/table)

| Sequence | Steps | Phases |
|---|---|---|
| `BUSINESS_ACCELERATION_CYCLE` | `REVENUE_ACCELERATION` appears -> persists into another reporting period -> a 3rd qualifying period with velocity `MODERATE`/`STRONG` | 1st -> FORMING, 2nd consecutive -> PROGRESSING, 3rd -> COMPLETE. Deliberately never requires PAT/margin - a pure business-growth read. |
| `OPERATING_LEVERAGE_CYCLE` | `REVENUE_ACCELERATION` -> `STRUCTURAL_MARGIN_EXPANSION` -> `PAT_ACCELERATION` (margin and PAT may land in the same reporting period; never require all 3 on the exact same date) | Revenue only -> FORMING; revenue + (margin OR PAT) -> PROGRESSING; all 3 within `stage3-earnings-cycle-max-quarter-gap` (default 4 quarters) -> COMPLETE |
| `MULTI_QUARTER_EARNINGS_EXPANSION` | `PAT_ACCELERATION` and/or `STRUCTURAL_MARGIN_EXPANSION` persisting >= `stage3-multi-quarter-earnings-min-persistence` (default 2) reporting periods | Can complete even when `OPERATING_LEVERAGE_CYCLE` doesn't - differentiates a one-quarter jump from real persistence |
| `INTEREST_COST_RELIEF_TREND` | `INTEREST_COST_DECLINING` persists 2+ additional quarters | Current Stage 2 scope only - `DELEVERAGING_CYCLE`/`BALANCE_SHEET_REPAIR`/`CASH_FLOW_TURNAROUND` stay **reserved, not implemented**, until live `DEBT_LEVEL`/`CASH_FLOW_FROM_OPERATIONS` evidence exists (same blocked-state discipline §8/§11 of docs/007 already use) - never infer debt reduction merely because interest expense declined |

### 14.2 Ownership (`ownership.transformation`) - blocked on §4's `as_of_date` fix first

| Sequence | Steps | Phases |
|---|---|---|
| `INSTITUTIONAL_OWNERSHIP_BUILDING` | `FII_ACCUMULATION` or `DII_ACCUMULATION` -> `INSTITUTIONAL_OWNERSHIP_EXPANSION` in a subsequent or same qualifying period; optional strengthening: `BULK_BUYING_WITH_OWNERSHIP_EXPANSION` | FII/DII alone -> FORMING; expansion follows -> PROGRESSING; expansion persists `stage3-ownership-building-min-persistence` periods OR bulk-buying evidence appears -> COMPLETE. Bulk activity is optional, never required - most genuine transitions have no disclosed bulk/block trade. |
| `BROAD_INSTITUTIONAL_PARTICIPATION` | `FII_ACCUMULATION` AND `DII_ACCUMULATION` within the configured span | Independent of promoter behavior - differentiates FII-only from FII+DII both moving |
| `PROMOTER_INSTITUTION_ALIGNMENT` | `PROMOTER_HOLDING_INCREASE` plus one of `FII_ACCUMULATION`/`DII_ACCUMULATION`/`INSTITUTIONAL_OWNERSHIP_EXPANSION` | Descriptive, not automatically positive - both moved up in the window, no more claimed |

**Contradiction handling**: `OWNERSHIP_CONTRADICTION` appearing while `INSTITUTIONAL_OWNERSHIP_BUILDING`
is `FORMING`/`PROGRESSING` does **not** automatically break the sequence - add reason
`PROMOTER_DILUTION_PRESENT`, reduce strength/confidence per rule, only escalate to `BROKEN` if the
contradiction persists beyond `stage3-ownership-contradiction-tolerance` (default 2 consecutive
periods). A single promoter-dilution event can legitimately accompany a real institutional
placement - don't over-react to one data point.

### 14.3 Market (`market.transformation`) - the cleanest implementation; built first (§15), the reference architecture

**Built (2026-09-21)**. Same package as Stage 1+2 (`market.transformation`), reusing the existing
package-private `ReasonCode` record as-is. Step detection reads **reason codes**, never
`primary_state` (Market's own priority ladder can mask a true sub-condition under a higher-ranked
composite state, same lesson Risk/Contradiction's Stage 2 build already established) - confirmed
against the real `MarketInflectionEngine` source that every reason code is unconditional on its
underlying boolean, regardless of which state wins that day. Trading-session gaps are counted by
real row index in the ascending history, never `ChronoUnit.DAYS.between(...)` -
`market.inflection_states` only ever has rows for real trading days.

| Sequence | Steps (reason codes) | Phases |
|---|---|---|
| `DELIVERY_LED_ACCUMULATION` | `DELIVERY_RISING` -> `DELIVERY_EXPANSION_SUSTAINED_5D` | FORMING on step 1; COMPLETE when Stage 2's own already-computed 5-day persistence reason fires - Stage 3 never recomputes persistence itself. |
| `STEALTH_ACCUMULATION_SEQUENCE` | `DELIVERY_RISING` AND `RELATIVE_VOLUME_RISING` (bounded pairing, see below) -> `VOLUME_DELIVERY_UP_PRICE_FLAT` | - |
| `MARKET_RECOGNITION_SEQUENCE` | `DELIVERY_EXPANSION_SUSTAINED_5D` -> `VOLUME_DELIVERY_UP_PRICE_FLAT` -> `BREAKOUT_FROM_STEALTH_ACCUMULATION` | **The reference sequence.** Adds a real temporal constraint Stage 2 itself doesn't enforce - Stage 2's own `STEALTH_ACCUMULATION_CANDIDATE` trigger never requires `SUSTAINED_DELIVERY_ACCUMULATION` to have happened first; Stage 3 does, and a dedicated test confirms the wrong order never completes it. |

Rule thresholds (`common.rule_definitions`, `common` `V16`, single-`ALWAYS`-condition rules read as
raw scalars, not metric-scoring ladders): `stage3-delivery-led-accumulation-max-gap` 3 sessions,
`stage3-stealth-formation-max-window` 20 sessions, `stage3-market-recognition-max-gap` 10 sessions,
`stage3-sequence-max-age` 40 sessions - all starting values, to revisit once real data accumulates.
**Break conditions stay conservative** - expiry only (`BROKEN`/`SEQUENCE_EXPIRED`) until Stage 2
actually produces an explicit negative/distribution state; no invented `DISTRIBUTION`/`BREAKDOWN`.

**Four real corrections caught during plan review, before any code was written**: (1) an explicit
`Attempt` lifecycle (`market.transformation.MarketTransformationSequenceEngine`'s private `Attempt`
record) - a brand-new `Attempt`, never a mutated old one, starts every time a sequence's first step
fires while no attempt is active or the active one is `COMPLETE`/`BROKEN`, so old evidence can never
leak into a later cycle. (2) `STEALTH_ACCUMULATION_SEQUENCE`'s two prerequisites (`DELIVERY_RISING`,
`RELATIVE_VOLUME_RISING`) must pair within `stage3-stealth-formation-max-window` sessions of each
other, each anchored to its own *first* occurrence since the last reset (never refreshed by a later
recurrence while still waiting) - the original draft's "two independent seen flags" had no expiry
and could wrongly pair evidence from unrelated formation attempts weeks apart. (3) Step
confidence/persistence are sourced from the reason code's own real underlying Stage 1 metric
(`DELIVERY_PERCENTAGE_20D_AVG`, `RELATIVE_VOLUME`, or `PRICE_RETURN_20D`; `VOLUME_DELIVERY_UP_PRICE_FLAT`
uses the minimum across whichever of the 3 are present), never the day's winning `primary_state`'s
driving-metric numbers - added one new ascending-history method to the existing
`MarketTransformationEvidenceReader` (`findHistory`, reusing its own `MarketEvidenceObservation`
record). (4) `market.transformation_sequence_readiness` is a **separate** table
(`instrument_id, as_of_date, history_sessions, readiness`, not per `sequence_type`), written every
real day independent of whether any sequence fired - keeps "no sequence row" honestly distinguishable
between "genuinely nothing formed" (`READY`) and "not enough real history to tell"
(`INSUFFICIENT_HISTORY`), the same lesson Sector's Stage 2 `SectorDataReadiness` already
established. `MISSING_PREREQUISITE_DATA` stays defined but structurally unreachable for Market -
all 3 sequences share the single `market.inflection_states` source.

**One real bug caught by live verification, not by any test**: `market.inflection_states.as_of_date`
is `Clock`-based (Market Stage 2's own real, already-approved design), so it does not always exactly
equal an individual metric's own latest real `trade_date` - confirmed live that a metric's evidence
can genuinely lag a day behind the state row it contributed to. The original reader merged Stage 2
reason-code history with Stage 1 evidence via an *exact* date match, silently dropping real evidence
whenever the dates didn't coincide (surfaced as every real sequence row showing `confidence = 0`).
Fixed with an as-of merge (`MarketInflectionHistoryReader.asOfEachStateDate` - latest evidence with
`tradeDate <= ` the state's date, a forward-pointer merge over two ascending lists), the same
as-of-not-exact-match principle Risk/Contradiction's `findStateAsOf` already established for a
different reason. Confirmed live afterward: ACE's real `DELIVERY_LED_ACCUMULATION` row correctly
shows `confidence = 90.00`, tracing exactly to `DELIVERY_PERCENTAGE_20D_AVG`'s own real evidence row
dated one day before the Clock-stamped state row.

**Live-verified against the real running app**: 60/60 instruments succeeded -
`DELIVERY_LED_ACCUMULATION` 54 `FORMING`, `STEALTH_ACCUMULATION_SEQUENCE` 42 `FORMING`,
`MARKET_RECOGNITION_SEQUENCE` 2 `FORMING`, none yet `COMPLETE`. Readiness: `INSUFFICIENT_HISTORY`
for all 60 - a real, honest, disclosed finding: `market.inflection_states` (Stage 2's own state
table) was never backfilled, only Stage 1's evidence was (`market-accumulation-evidence-backfill`,
earlier this build order) - it genuinely has only 1 real distinct `as_of_date` system-wide today, so
Stage 3 correctly reports "not enough history to tell" even while individual sequences correctly
detect `FORMING` from that one real day's reason codes (the two mechanisms are deliberately
independent, per correction 4). The new `market-transformation-sequences-backfill` job runs
correctly but, for the same reason, can only replay against that same single real day until Stage
2's own state table gets deeper real history. Re-triggered same-day: identical row count (98) and
full-table checksum - idempotent.

### 14.4 Capital Allocation (`corporate.transformation`) - structurally different (event sequences)

| Sequence | Steps |
|---|---|
| `REPEATED_CAPITAL_RETURN` | `BUYBACK_ACTIVITY` appearing in more than one qualifying rolling window/event cycle |
| `REPEATED_EQUITY_RAISE` | `EQUITY_RAISE_ACTIVITY` repeated within the configured window - **descriptive only, never named `DILUTION_CYCLE`** until real QIP/preferential-allotment/warrant/pricing/size/share-count classification exists (same disclosed-limitation discipline as docs/007 §11's `INSTITUTIONAL_CAPITAL_RAISE`/`DILUTION_PRESSURE`) |

A buyback + a rights issue for the same instrument produce **two independent sequence rows**, never
one blended verdict (§8's own per-`sequence_type` uniqueness already guarantees this structurally).
`MIXED_CAPITAL_ALLOCATION_ACTIVITY` (Stage 2's 4th state) isn't referenced by either sequence here -
not needed for a simple "did this repeat" read; reconsider once real data exists to see if it's
worth its own pattern. Reserved, not implemented: `INSTITUTIONAL_CAPITAL_RAISE_CYCLE`,
`STRATEGIC_CAPITAL_INJECTION`, `DILUTION_PRESSURE`, `CAPITAL_RETURN_CLUSTER`.

### 14.5 Sector (`sector.transformation`)

| Sequence | Steps |
|---|---|
| `SECTOR_TAILWIND_SEQUENCE` | `SECTOR_STRENGTHENING` -> `STOCK_OUTPERFORMING_NIFTY` |
| `STOCK_LEADERSHIP_EMERGENCE` | `SECTOR_STRENGTHENING` -> `STOCK_OUTPERFORMING_SECTOR` -> `NEW_LEADERSHIP_EMERGENCE` - meaningfully stronger than outperforming Nifty alone, since the stock is beating its own (improving) peer group |
| `IDIOSYNCRATIC_LEADERSHIP` | `STOCK_OUTPERFORMING_SECTOR` fires while `SECTOR_STRENGTHENING` has not - relative strength is company-specific, not sector beta. Real, valuable input for Stage 4. |

Timing in trading sessions, 20-60 depending on pattern (rule-configured, §7). **Note, carried over
from Stage 2's own live-verified finding**: `STOCK_OUTPERFORMING_SECTOR`/`NEW_LEADERSHIP_EMERGENCE`
are currently silent system-wide (`reference.sector_benchmarks` is empty) - so
`STOCK_LEADERSHIP_EMERGENCE`/`IDIOSYNCRATIC_LEADERSHIP` will build correctly but produce zero real
rows until that table is populated, the same honest "built, logic-complete, currently silent"
posture every blocked Stage 2 state already has. `SECTOR_TAILWIND_SEQUENCE` is real and buildable
today.

### 14.6 Risk/Contradiction - no standalone Stage 3 yet

Contradictions stay inside the domain sequence they affect for now (Ownership's
`PROMOTER_DILUTION_PRESENT` reason above is the pattern). A cross-domain aggregation
(`PERSISTENT_OWNERSHIP_CONTRADICTION`, `PERSISTENT_FINANCIAL_CONTRADICTION`,
`CAPITAL_STRUCTURE_STRESS`) belongs after Risk's own Stage 2 evidence family is genuinely live with
real triggering data (today it's real but thin - `risk.contradiction_states` currently shows
`GROWTH_QUALITY_CONTRADICTION` 11/60, `OWNERSHIP_CONTRADICTION` 2/60,
`MULTI_DOMAIN_CONTRADICTION` 2/60, live-verified at Stage 2 build time). `risk` V3 stays reserved,
unused by this initial catalogue.

## 15. Recommended build order

1. **Market** - **built 2026-09-21**, see §14.3. Deepest daily history, cleanest Stage 2 states, no
   quarterly filing-date complications; the Stage 3 reference implementation every later domain
   mirrors (explicit `Attempt` lifecycle, bounded prerequisite pairing, reason-code-sourced
   confidence, separate readiness table, point-in-time backfill).
2. **Ownership** - already mature Stage 2 data, but fix the `as_of_date` cadence bug (§4) first, and
   quarterly cadence makes temporal logic meaningfully different from Market's.
3. **Financial** - important, but needs careful period/publication-date handling (§16).
4. **Sector** - daily data, but the benchmark-mapping gap (§14.5) adds a real complication.
5. **Capital Allocation** - structurally different (event sequences, not state-transition sequences).
6. **Risk** - later, once its own Stage 2 evidence is genuinely live, not thin.

## 16. Point-in-time correctness - non-negotiable

When evaluating `asOfDate = 2026-06-30`, the engine may only read Stage 2 states with
`as_of_date <= 2026-06-30` - never latest-current data. Provide a callable
`evaluateAsOf(instrumentId, asOfDate)` (or a dedicated backfill orchestrator, mirroring the
`backfill()` methods every Stage 1 engine already has) so historical sequences can be reconstructed
after the fact without look-ahead bias. Phase 4/5 learning depends on this being correct.

**Financial filing-timing caveat, already disclosed in docs/007 §6-8/§13 and unchanged by this
doc**: `financial.transformation_evidence`/`financial.inflection_states` currently only carry
`period_end`, not the date a result was actually published. A quarter ended June 30 was not known
on June 30. Stage 3's point-in-time backfill for Financial sequences inherits this exact same
limitation - real, disclosed, not yet blocking (no `available_from`/`source_published_at` column
exists today) - but must be resolved before any serious historical backtesting of Financial
sequences, exactly as already stated in docs/007.

## 17. Explainability

Stage 3 must produce a deterministic, non-LLM narrative from persisted reasons alone - e.g.
"`MARKET_RECOGNITION_SEQUENCE` COMPLETE, strength 88, confidence 94%: delivery accumulation became
sustained on 12 Aug; relative volume expanded on 16 Aug; price remained within the quiet-price
range; early price participation appeared on 22 Aug." An LLM-generated explanation on top is
optional future polish - the deterministic facts must already be there. Every reason should carry a
stable `evidence_reference` back to the Stage 2 row/Stage 1 evidence that caused it (e.g.
`"market.inflection_states:<uuid>"`), not only a human-readable string, for this reason.

## 18. Reason codes

Shared conceptual vocabulary, implemented as domain-local `ReasonCode` records (same "duplicated
per package, never shared across module-internal boundaries" convention every Stage 2 family
already follows): `FIRST_STEP_DETECTED`, `SECOND_STEP_DETECTED`, `ALL_REQUIRED_STEPS_COMPLETE`,
`PERSISTENCE_REQUIREMENT_MET`, `OPTIONAL_SUPPORTING_EVIDENCE`, `STEP_GAP_EXCEEDED`,
`SEQUENCE_EXPIRED`, `CONTRADICTORY_EVIDENCE`, `INSUFFICIENT_HISTORY`. Domain-specific reasons layer
on top (Market: `DELIVERY_EXPANSION_STARTED`, `DELIVERY_PERSISTENCE_CONFIRMED`,
`RELATIVE_VOLUME_JOINED`, `STEALTH_ACCUMULATION_DETECTED`, `PRICE_PARTICIPATION_STARTED`; Financial:
`REVENUE_ACCELERATION_STARTED`, `MARGIN_EXPANSION_FOLLOWED`, `PAT_ACCELERATION_FOLLOWED`,
`MULTI_QUARTER_PERSISTENCE`; Ownership: `FII_ACCUMULATION_STARTED`, `DII_ACCUMULATION_STARTED`,
`BROAD_INSTITUTIONAL_EXPANSION`, `BULK_BUYING_SUPPORT_PRESENT`, `PROMOTER_DILUTION_PRESENT`;
Sector: `SECTOR_STRENGTHENED`, `STOCK_OUTPERFORMED_NIFTY`, `STOCK_OUTPERFORMED_SECTOR`,
`LEADERSHIP_STATE_REACHED`).

## 19. Engine/orchestrator pattern - identical shape to every Stage 2 family

Pure calculation engine, no JDBC: `<Domain>TransformationSequenceEngine.evaluate(instrumentId,
symbol, asOfDate, stage2History, ruleSet)`. Reader gathers history -> engine calculates -> writer
persists -> orchestrator coordinates the per-instrument try/catch loop (one bad symbol never fails
the whole universe, same log-and-continue convention every Stage 2 orchestrator already uses).

Suggested per-domain class set (`market` shown, same pattern for `financial`/`ownership`/`sector`/
`corporate`): `MarketSequenceType`, `MarketSequencePhase`, `MarketSequenceReadiness`,
`MarketInflectionHistoryReader`, `MarketTransformationSequenceEngine`,
`MarketTransformationSequenceWriter`, `MarketTransformationSequenceOrchestrator`,
`MarketTransformationSequenceScheduler`, `MarketSequenceResult`, `MarketSequenceReasonCode`.

## 20. Scheduling

Register every recurring sequence job with the existing `JobRegistry`/`CronMonitoringRepository`
monitoring infrastructure, same as every Stage 2 job. Policy: `Stage3(domain)` runs 5-10 minutes
after `Stage2(domain)` - do not hardcode final cron times in this spec; inspect the real, current
`JOB_SCHEDULES` map at implementation time (it will have shifted from what's listed in docs/007 §15.6
as more jobs land) and pick free slots the same way every Stage 2 job's cron was confirmed live
before being written down.

## 21. Testing (per domain, generic list)

No history; insufficient history; first step only; correct ordered progression; same-period valid
progression; wrong ordering; maximum gap exceeded; complete sequence; sequence expiry;
contradictory evidence; same-day rerun (must not create a second step or advance phase); confidence
calculation; sequence strength calculation; reason codes; rule version. Domain-specific cases called
out explicitly in the user's original working notes (Financial: revenue-only forming, 3-period
complete, revenue->margin progressing, all-three complete, PAT-years-before-revenue must NOT
complete, interest-relief multi-period; Ownership: FII-only forming, FII->expansion, FII+DII
broad-participation, promoter+institutional alignment, one dilution doesn't break, persistent
contradiction does break; Market: delivery-only forming, sustained-complete, stealth progression,
stealth-then-participation-complete, same-day-retry-no-fake-step, gap-exceeded-broken; Sector:
tailwind forming/complete, leadership-emergence complete, idiosyncratic-without-sector-strengthening;
Capital: buyback forming/complete, raise forming/complete, buyback+raise = two independent rows not
one blend) - carry all of these into each domain's real test suite when implementing.

## 22. Integration verification (per domain)

`./gradlew build` green -> restart app -> trigger Stage 1 if needed -> trigger Stage 2 -> trigger
the new Stage 3 job -> query the sequence table -> pick a real instrument -> trace every step back
to actual Stage 2 historical rows -> verify dates/order by hand -> re-trigger same day -> confirm no
duplicate rows, no phase advance from the rerun alone, reasons replaced not accumulated -> confirm
the admin monitoring endpoint reports the execution. Same discipline every Stage 2 family's live
verification already used throughout this build order.

## 23. Definition of Done

Stage 3 is complete only when AlphaGraph can answer, for any tracked company: *which
transformations are currently forming, progressing, or complete; what sequence of evidence created
them; when did each transformation begin; how strong and reliable is it; and what evidence would
cause it to expire or break* - and that answer must be reproducible historically without using
future data (§16). Stage 4 (cross-domain convergence) and Stage 5 (lifecycle) are explicitly out of
scope for this work; Stage 3's job is to hand Stage 4 clean, historically reproducible, point-in-time
transformation sequences, nothing more.
