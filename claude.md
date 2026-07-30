Module 0.1 status: DONE (docs/001-005 written, committed, pushed)
Module 0.2 status: DONE (tech stack below; Python NLP/ML sidecar added for corporate/learning modules, Phase 2+)
Module 0.3 status: DONE (15-module Gradle scaffold matching the module map below, ArchUnit module-boundary test in bootstrap, verified: ./gradlew build passes and the app boots)
Module 0.4 status: DONE (Flyway migrations for common/reference/scheduler/api schemas per docs/003_Database_Architecture.md, verified against a real local Postgres 17 instance - schemas, tables, indexes, constraints, seed data, and the updated_at trigger all confirmed working)
Module 0.5 status: DONE (Collector/Parser/Validator/Normalizer/Loader + Pipeline orchestrator + RequiredFieldsValidator in the common module, 5 passing unit tests covering success/partial/failure paths)
Module 0.6 status: DONE (DataQualityEngine in common.quality - DataQualitySpec/DataQualityInput/DataQualityScore + equal-weight placeholder formula, 8 passing unit tests, kept independent of both the ETL framework and any domain module - scheduler wires the two together in Module 0.8)
Module 0.7 status: DONE (RuleEvaluator in common.rules - Rule/RuleCondition/MetricContext/EvaluationResult + ArithmeticRuleEvaluator covering all 6 operators, 9 passing unit tests. Also fixed a real V1 schema gap: rule_conditions needed a second upper_bound column for BETWEEN, added via V2 migration, verified against real Postgres)
Module 0.8 status: DONE (PipelineOrchestrator in the scheduler module wires Pipeline + DataQualityEngine + quality gate + NullEngine.calculate + Notify, per docs/002_Engine_Architecture.md §6. Persists to scheduler.pipeline_executions/pipeline_execution_errors and common.data_quality_scores via JdbcPipelineExecutionRecorder, behind a PipelineExecutionRecorder interface so it's unit-testable without a DB. DailyPipelineScheduler runs a dummy proof pipeline on a 6PM IST cron plus on demand. Verified end-to-end against real Postgres: SUCCESS row + 1.0 quality score correctly linked)
Module 0.9 status: DONE (REST + OpenAPI/Swagger + JWT + versioning, all 7 Phase 0 endpoints from docs/004_API_Architecture.md §6 implemented and verified end-to-end against a live instance: POST /api/v1/auth/login, GET/POST pipeline-definitions incl. {id}/run, GET pipeline-executions incl. {id}, GET/POST rule-definitions incl. {id}/activate. Seeded dev admin: admin@alphagraph.local / AlphaGraph@2026. Found and fixed 3 real bugs during verification: missing -parameters compiler flag broke every @PathVariable/@RequestParam, GlobalExceptionHandler was swallowing exceptions without logging, and springdoc-openapi 2.6.0 was incompatible with Spring Boot 3.5's Spring Framework version (bumped to 2.8.6). api module now legitimately depends on scheduler - ArchUnit rule and 001_System_Architecture.md updated to match)
Module 0.10 status: DONE (Rows/Duration/Failures/Status logging already existed from Module 0.8's pipeline_executions table; this module added the two missing pieces from docs/005_Deployment.md §5: structured JSON console logs via logstash-logback-encoder, and a correlation_id column + CorrelationIdFilter + MDC plumbing so a pipeline_executions row can be traced back to the X-Request-Id of the API call that triggered it, or a generated cron-<uuid> for scheduled runs. retry_count remains a real but currently-unused column - no automatic retry policy exists yet, undocumented anywhere, so none was invented. Verified end-to-end against a live instance: explicit and auto-generated correlation ids both echo back, persist correctly, and appear as a "requestId" field on the matching JSON log line)

PHASE 0 (Platform Foundation) COMPLETE - all 10 modules done. Running system: scheduler runs a dummy pipeline (cron or on-demand), data quality + rule engines score it, REST API (JWT-secured, OpenAPI-documented) reads it all back. No AI, no dashboards, no stock recommendations, per the Phase 0 mandate.

Phase 1 (Intelligence Foundation) in progress:
Module 1.1 status: DONE (market data source - real NSE data, not synthetic. market.daily_prices stores OHLC/volume/delivery_percentage, sourced from NSE's sec_bhavdata_full format; market cap deliberately excluded, no real file source confirmed yet. Bundled sample = 9 real rows for 8 seeded instruments + one deliberately-unresolvable real row (20MICRONS) proving the quarantine path. MarketDataScheduledPipeline self-registers via the new ScheduledPipeline registry from Module 1.1 prep. Fixed a real fabricated-ISIN bug for BEML found via the real security master download, and a real gap where FlywayMultiSchemaConfig never had "market" in its migration list. Verified end-to-end: 8/9 rows loaded correctly, matched byte-for-byte against the source CSV)
Module 1.2 status: DONE (ownership data - promoter/FII/DII/MF shareholding. ownership.shareholding_pattern stores period_end + promoter/fii/dii/mf/public percentages, unique on (instrument_id, period_end), no cross-schema FK per the schema-per-module rule. Real-data gap disclosed and accepted by user: NSE doesn't publish shareholding pattern in a free bulk format (filed per-company via NEAPS/XBRL; bulk access is a paid NSE Corporate Data Subscription) - so this ships as a manually-compiled synthetic sample (9 rows): real researched percentages for RELIANCE/TCS/INFY/HDFCBANK/ICICIBANK/ESCORTS, unverified representative estimates for BEML/ACE, plus a deliberately-unresolvable ZOMATO row proving the quarantine path. A real automated source is deferred to a later module. ShareholdingScheduledPipeline self-registers via the same ScheduledPipeline registry as market. Found and fixed 3 real Spring DI bugs while wiring a second domain module into the registry: (1) ownership's InstrumentLookup and market's InstrumentLookup collided on the same default bean name from different packages - renamed to OwnershipInstrumentLookup; (2) ShareholdingScheduledPipeline was written against the generic Collector<List<String>> interface with no actual need for runtime swapping - narrowed to the concrete ShareholdingCollector; (3) root cause of the remaining NoUniqueBeanDefinitionException - market's own pipeline still depends on Collector<List<String>> generically for its @Profile-based swap, and ShareholdingCollector implements that same parameterized type, so it was a false-positive candidate there too - fixed with @Qualifier("market") on BhavdataCollector, HttpBhavdataCollector, and MarketDataScheduledPipeline's constructor. Also fixed an unrelated flaky JwtServiceTest (tampering the JWT's last character occasionally decoded to the same bytes due to base64url "don't care" bits - now tampers a character inside the payload segment, verified deterministic over 8 runs). Verified end-to-end against real Postgres: both nse-daily-bhavdata and ownership-shareholding-pattern pipelines run together correctly; shareholding pipeline reads 9 rows, accepts 8, rejects 1 (ZOMATO, unknown instrument), all 8 land with correct values)
Module 1.3 status: DONE (financial results/fundamentals - Sales/PAT/EPS/ROE/ROCE/margins/cash-flow. financial.financial_results stores period_end + period_type + all 7 metrics, unique on (instrument_id, period_end, period_type), no cross-schema FK. Only sales and pat are NOT NULL - every listed company reports those two every period; everything else is nullable since it isn't uniformly available or even uniformly meaningful (banks/NBFCs don't report a conventional "sales" line - the sample substitutes net interest income there, flagged as needing a sector-aware definition once the Fundamental Engine exists). Same real-data gap as Module 1.2, re-confirmed via fresh research: no free bulk source for financial results, filed per-company as XBRL/PDF behind paid subscriptions. Ships as a manually-compiled sample (9 rows, Q4 FY25): real researched Sales/PAT for all 8 resolvable companies, EPS/ROE/ROCE/margins wherever a source reported them directly for RELIANCE/TCS/INFY/HDFCBANK/ICICIBANK (net_margin for RELIANCE/INFY derived from PAT/sales, noted as such), ESCORTS' real sales/PAT with everything else left null rather than guessed, BEML/ACE as unverified estimates, plus a deliberately-unresolvable ZOMATO row proving the quarantine path. FinancialResultsScheduledPipeline self-registers via the same ScheduledPipeline registry as market/ownership. Applied the 3 Spring DI lessons from Module 1.2 correctly the first time with no new bugs: FinancialInstrumentLookup uniquely named, and the pipeline depends on the concrete FinancialResultsCollector rather than the generic Collector interface. Verified end-to-end against real Postgres: all four registered pipelines (dummy, market, ownership, financial) run together correctly; financial-quarterly-results reads 9 rows, accepts 8, rejects 1 (ZOMATO, unknown instrument), all 8 land with correct values)
Module 1.4 status: DONE (corporate actions - dividend/bonus/split/rights/buyback. corporate.corporate_actions stores action_type + ex_date + record_date/announcement_date + type-specific fields (dividend_amount; ratio_numerator/ratio_denominator; price), unique on (instrument_id, action_type, ex_date), no cross-schema FK. Only instrument_id, action_type, and ex_date are NOT NULL - those three apply to every action regardless of type. Same real-data gap as Modules 1.2/1.3, re-confirmed via fresh research: NSE's bulk corporate actions report goes out over its Extranet to clearing members only, not publicly, and the interactive site's per-symbol actions API is unofficial and session-based. Unlike 1.2/1.3, this sample has NO estimated/fabricated rows: estimating the magnitude of a metric every company reports (quarterly PAT, promoter %) is approximating a real fact, but guessing whether a specific corporate event happened would be inventing one - so the sample only includes the 6 corporate actions that were actually confirmed (real FY25 final dividends for RELIANCE/TCS/INFY/HDFCBANK/ICICIBANK/ESCORTS with real ex-dates/record-dates/amounts), plus a deliberately-unresolvable ZOMATO row proving the quarantine path. No BONUS/SPLIT/RIGHTS/BUYBACK events were found for these companies in the current period, so only DIVIDEND rows appear - the schema still models the other types via action_type and the nullable ratio/price columns. CorporateActionsScheduledPipeline self-registers via the same ScheduledPipeline registry as market/ownership/financial. Applied the Spring DI lessons from Module 1.2 correctly again with no new bugs: CorporateInstrumentLookup uniquely named, pipeline depends on the concrete CorporateActionsCollector rather than the generic Collector interface. Verified end-to-end against real Postgres: corporate-actions reads 7 rows, accepts 6, rejects 1 (ZOMATO, unknown instrument), all 6 land with correct ex-dates/record-dates/dividend amounts)

All 6 Phase 1 data sources from the architecture diagram are now live: Bhavcopy, Deliverable Report (both Module 1.1), Financial Filings (1.3), Shareholding Filings (1.2), Corporate Actions (1.4). Security Master remains a one-time seed (reference.instruments via migration), not an ongoing ingested pipeline - worth revisiting if new listings/symbol changes need to flow in automatically.

Module 1.5 status: DONE (Technical Engine - the first real Phase 1 scoring engine, not another ETL data source. Produces Trend/Momentum/Volume/Breakout/Stage/Market Behaviour Score from price and volume alone, per the roadmap spec. technical.technical_scores stores the classification plus every raw indicator value (sma20/50/200, weekly_sma30, rsi14, macd_line/signal/histogram, adx14, atr14, obv, relative_volume) for traceability. Historical data gap closed with real data: backfilled 75 real, distinct trading days (2026-04-09 through 2026-07-28) for the 8 seeded instruments from NSE's real per-day sec_bhavdata_full archive (same source/format as Module 1.1, confirmed it serves historical dates too), written into market's V2 seed migration as real committed data rather than a live fetch-on-startup. sma200 and weekly_sma30 (needed for Stage) correctly stay null - both need more history (200 days, ~150 days) than 75 days provides; the engine reports this as genuinely unavailable, no guessing. Architecture discovery via the existing ArchUnit ModuleBoundaryArchTest (first time it had real cross-domain classes to actually check): technical cannot depend on market directly (domain modules never depend on each other, docs/001_System_Architecture.md §4 Rule 3) - this is a real read of upstream price history, not a simple symbol lookup like ownership/financial/corporate's InstrumentLookup pattern. Per Rule 4, intelligence is the module allowed to depend on two domain modules at once, so this is intelligence's first real code: market.api.DailyPriceReader (a new published read interface) + intelligence.technical.TechnicalAnalysisOrchestrator, which reads market's history through that interface, maps it to technical's own DailyBar type, calls TechnicalEngine.calculate(), and persists through technical's own TechnicalScoreWriter - technical itself does zero I/O. Rule-driven scoring: seeded 7 technical-* rules in common.rule_definitions/rule_conditions (price-above-sma50/200, RSI momentum zone, MACD bullish, ADX trending, relative volume, OBV rising), loaded by RuleSetLoader and evaluated via the existing ArithmeticRuleEvaluator from Module 0.7 - editable later via the existing rule-definitions CRUD API, no redeploy needed. Descoped per user decision: the roadmap's 10/30-minute intraday candles have no free historical source (paid vendor data, unlike daily bhavcopy) - daily-bar indicators only, disclosed as a deferred gap. Found and fixed the same recurring bug as every prior new-schema module: FlywayMultiSchemaConfig never had "technical" in its migration list. Verified end-to-end against real Postgres: all 8 instruments produced real, differentiated scores (varying trend/momentum/volume states matching each instrument's actual recent price action); full build green including ArchUnit with real classes in every domain module for the first time)
Module 1.6 status: DONE (Fundamental Engine - answers "is the business becoming stronger?" from financial statements alone, no price/charts, per the roadmap spec. Produces Business Growth/Profitability/Capital Efficiency/Financial Quality/Financial Score into financial.fundamental_scores. Two real data gaps closed via research (both per explicit user direction): (1) Growth metrics need a same-period-type prior year - financial_results had only Q4 FY25, so researched real Q4 FY24 figures for RELIANCE/TCS/INFY/HDFCBANK/ICICIBANK/ESCORTS and added a second real row each (V2 migration + extended CSV); TCS's Q4 FY24 EPS was deliberately left null despite an initial search hit exactly matching Q4 FY25's EPS - looked like a citation mix-up between exhibit columns rather than a confirmed distinct figure, so left null rather than risk asserting a wrong number. BEML/ACE/ZOMATO get no second period. (2) Efficiency (Asset Turnover, Working Capital, Cash Conversion) and Leverage (Debt, Interest Coverage, Debt/Equity) needed balance sheet fields financial_results never had - added via migration (total_assets/current_assets/current_liabilities/total_debt/total_equity/interest_expense/ebit, all nullable) and researched real FY25 figures for RELIANCE/TCS/INFY/ESCORTS; deliberately left null for HDFCBANK/ICICIBANK since a bank's balance sheet doesn't map onto "current assets/liabilities" or conventional "debt" without a sector-aware model that doesn't exist yet (same caveat class as Module 1.3's NII-as-sales substitution). Architecture: unlike Module 1.5's Technical Engine, this needed NO intelligence-bridging - financial already owns financial_results (Module 1.3), so FundamentalAnalysisOrchestrator reads via FinancialResultReader and writes via FundamentalScoreWriter entirely within the financial module, no cross-domain dependency. Found and fixed the same bean-name collision bug as Module 1.2: financial's RuleSetLoader collided with technical's RuleSetLoader (Module 1.5) despite being in different packages - renamed to FundamentalRuleSetLoader. Rule-driven scoring: seeded 8 fundamental-* rules (revenue/PAT growth bands, ROE/ROCE/net-margin quality bands, asset turnover, interest coverage, debt/equity) evaluated via the existing ArithmeticRuleEvaluator from Module 0.7. Verified end-to-end against real Postgres: revenue/PAT growth matched hand-calculated values exactly (RELIANCE +8.59%, TCS +5.29%, INFY +7.92% revenue; INFY -11.75% PAT correctly flagged as a real decline), RELIANCE's computed interest coverage of 5.37x matched the externally-researched figure exactly, TCS correctly flagged DECLINING profitability from real YoY net margin compression, banks correctly show null efficiency/leverage throughout)
Module 1.7 status: DONE (Institutional Engine - answers "what is smart money doing?" from promoter/FII/DII/MF shareholding trend, delivery %, volume, and real bulk/block deals, per the roadmap spec. Produces Promoter/FII/DII/MF status, Delivery status, Institutional Behaviour, Institutional Score into ownership.institutional_scores. Turned out to need a hybrid of Module 1.5's and Module 1.6's architecture, not purely the same-module pattern predicted last entry: delivery%/volume are market's data (ownership can't read it directly, Rule 3), so intelligence.institutional bridges that part exactly like Module 1.5; shareholding trend and bulk/block deals are ownership's own data, read directly with no bridging, exactly like Module 1.6. Two real data pieces added: (1) researched a real March 2026 shareholding quarter for RELIANCE only (promoter 50.01->50.48%, FII 18.67->17.19%, DII 20.46->21.04%) to enable trend detection - skipped TCS/ICICIBANK/ESCORTS/INFY/HDFCBANK since their research turned up no distinct figure or one using an incompatible DII definition that would fabricate a trend from a definitional mismatch (same caution as Module 1.6's TCS EPS call). (2) Discovered NSE genuinely publishes Bulk/Block Deals for free and live (archives.nseindia.com/content/equities/bulk.csv and .../block.csv) - unlike every other real-but-gated ownership/financial source found so far. Built a real daily ETL pipeline (ownership.deals) with the same bundled-sample/live-HTTP split as market's bhavdata pipeline, @Qualifier("ownership-bulk-deals") to avoid the Collector<List<String>> interface collision (Module 1.2's lesson recurring again). Real constraint: the URL has no date parameter, so it only ever exposes the CURRENT day - no historical backfill possible the way Module 1.5 backfilled prices; real history accumulates day-by-day from here. Verified against the real live files: none of our 8 seeded instruments had bulk deals that day (expected - bulk/block deals concentrate in small/micro-caps whose float is thin enough to cross NSE's reporting threshold; our large/mid-cap universe rarely does), so all 20 real rows correctly quarantined as "unknown instrument" - proves the real pipeline mechanics work end to end even with an unlucky draw for our tracked universe. Rule-driven scoring: seeded 7 institutional-* rules evaluated via the existing ArithmeticRuleEvaluator. Verified end-to-end against real Postgres: RELIANCE's promoter/FII/DII changes matched hand-calculated values exactly (+0.47pp/-1.48pp/+0.58pp), institutional_score (55.00) and confidence (82.86) both independently recomputed to the exact same values by hand from the rule weights and metric-availability count)
Module 1.8 status: DONE (Sector Engine - answers "where is money moving?" by comparing SECTORS, not individual stocks, per the roadmap spec. The first engine that aggregates across MANY instruments into one score rather than scoring a single instrument (Technical/Fundamental/Institutional, Modules 1.5-1.7, were all one-instrument-in, one-score-out). Produces Leadership/Momentum/Rotation/Money Flow/Sector Score into sector.sector_scores. Real sector mapping added to reference - "Sector Mapping" never existed before (reference.instruments had no sector_id column at all, and Module 0.4's "Industrials > Capital Goods > Construction & Engineering" tree was explicit dummy data, never linked to any instrument). Classifications are standard NSE sector groupings, textbook enough to need no external research: TCS/INFY -> Information Technology, HDFCBANK/ICICIBANK -> Financial Services, ESCORTS/BEML/ACE -> Capital Goods, RELIANCE -> Energy (alone - a real, disclosed consequence of only tracking 8 instruments). Architecture: sector.mapping reads reference.sectors/instruments directly (reference is a shared kernel every module already reads freely, not a restricted peer domain), but sector.engine cannot read market's price/volume history directly, so intelligence.sector bridges that part like Modules 1.5/1.7 - with a new wrinkle: it assembles price history for EVERY tracked instrument, not just one, since Relative Strength needs a whole-market baseline to compare each sector against. Three real bugs found and fixed during verification: (1) picked "V3" for reference's new migration without checking existing versions - Module 1.1 already has a V3 (BEML fix) - renamed to V4; (2) reference.sectors has no unique constraint on name, so inserting a second "Capital Goods" row collided with Module 0.4's dummy tree's existing row by that name, Postgres correctly rejected the resulting ambiguous lookup - fixed by reusing the existing row instead of duplicating it; (3) the same recurring bug as every prior new-schema module - "sector" was missing from FlywayMultiSchemaConfig. Rule-driven scoring: seeded 5 sector-* rules (relative strength, breadth, participation, volume expansion, performance). Leadership (5-day trend) and Rotation (10-day trend) are deliberately measured over different windows so they carry distinct meaning rather than duplicating each other. Verified end-to-end against real Postgres using the 75 real trading days already backfilled (Module 1.5): all 8 instruments correctly mapped to their 4 sectors; all 4 sectors produced real, differentiated scores (Information Technology: VERY_STRONG momentum, +3.82pp relative strength; Energy/RELIANCE alone: DECREASING leadership, NEGATIVE rotation); confidence scaled by constituent_count exactly as designed (100/50/75/75 for counts of 3/1/2/2))

Module 1.9 status: DONE (Risk Engine - answers "what could invalidate this investment?" per the roadmap spec, by aggregating already-computed output from three Phase 1 engines rather than raw source data - the most cross-cutting engine yet, reaching into Technical (1.5)/Fundamental (1.6)/Institutional (1.7)'s score tables plus a derived Valuation read. Produces Business/Technical/Ownership/Valuation/Overall Risk + Risk Score into risk.risk_scores. Event Risk (litigation, corporate governance, pledged shares, auditor resignation) deliberately descoped per explicit user direction - no free structured data source confirmed real for any of those four signals, unlike everything else this engine reads. Architecture: added TechnicalScoreReader/FundamentalScoreReader/InstitutionalScoreReader to their own modules' .engine packages (each module previously only needed a ScoreWriter, since nothing had to read a score back until Risk needed it as input); risk.api.RiskEngineInput is a flat record of plain String/Double fields rather than other modules' enum types, since risk may never import another domain module's package tree (Rule 3) - intelligence.risk converts each domain enum to its name() before handing off, the same established practice as Module 1.7's orchestrator reaching past .api into .engine directly. RiskEngine converts every categorical signal into a signed +1/0/-1 metric and scores Business/Technical/Ownership/Valuation independently via risk-* sub-prefixes before averaging into an overall score - same 0-100 scale and same direction as every prior engine (higher still means safer), achieved with bidirectional rule weights (positive for safety, negative for risk) rather than a new evaluator; ArithmeticRuleEvaluator from Module 0.7 already just sums whatever weight matches. PE/PB are derived from real data only, never freshly sourced: PE = latestClose/eps; PB uses an implied shares-outstanding (pat/eps) to get book value per share = totalEquity*eps/pat. Self-caught a JDBC bug before running any code (applying Module 1.x's growing BigDecimal-vs-Double lesson proactively): getObject() on a numeric column returns BigDecimal, not Double, in all three new Score Readers. Seeded 13 risk-* rules (V7 migration), weighted so each of the 4 categories can independently reach both VERY_LOW and VERY_HIGH regardless of how many signals feed it. Found and fixed the same recurring bug as every prior new-schema module proactively this time (before verification, not during): "risk" was missing from FlywayMultiSchemaConfig. Verified with 3 unit tests (best-case/worst-case/mixed-signal, hand-computed against the seeded weights) plus a live end-to-end run against real Postgres: all 8 real tracked instruments produced plausible, internally-consistent scores (TCS: VERY_LOW technical risk despite STRONG_UPTREND/IMPROVING/STRONG signals, but VERY_HIGH valuation risk from its real PE=70.97/PB=9.16; RELIANCE: HIGH technical risk matching its real STRONG_DOWNTREND). Real environment constraint hit and disclosed: the local Postgres service required an admin-rights start this session (native Windows service, not the docker-compose instance) - user started it directly.)

PHASE 1 (Intelligence Foundation) COMPLETE - all 9 modules done. All 6 Phase 1 data sources (Bhavcopy, Deliverable Report, Financial Filings, Shareholding Filings, Corporate Actions, Bulk/Block Deals) and all 5 Phase 1 engines (Technical/Fundamental/Institutional/Sector/Risk) are live, rule-driven, and verified against real Postgres data for all 8 tracked instruments.
Next: Phase 2 - not yet scoped in detail in this file; see the roadmap for what follows Intelligence Foundation.

Local dev note: the scheduler's admin recovery endpoint (POST /pipeline-definitions/{id}/run) needs a pipeline's DB id, which only exists after its first run - a brand-new pipeline can't be triggered that way until either the 6PM cron fires once, or you call DailyPipelineScheduler.runScheduledPipelines() directly (e.g. via a test) to force the first registration.

Module 1.1 follow-up - real HttpBhavdataCollector: DONE. Confirmed https://archives.nseindia.com/products/content/sec_bhavdata_full_DDMMYYYY.csv is live (contradicts a stale "discontinued 2024" claim found during research - likely conflated with a different, retired plain-bhavcopy report). HttpBhavdataCollector active only in docker/prod profiles (@Profile), bundled sample stays default for local/CI. URL template configurable via alphagraph.market.nse-bhavdata-url-template. Fixed two real bugs found via live verification: (1) PipelineOrchestrator always said "Completed" even on a FAILED/zero-rows run - now short-circuits on rowsRead==0 with a clear "FAILED - <reason>" message, skipping quality scoring entirely; (2) HttpBhavdataCollector's two constructors confused Spring DI (tried a nonexistent no-arg constructor) until @Autowired was added - every other bean in the codebase happens to have exactly one constructor, so this class of bug hadn't surfaced before. Verified against the real live NSE server: 2,408 real rows fetched, 8 loaded, 2,400 correctly quarantined, no crash.

Local dev environment notes (for resuming without rediscovery):
- Repo: https://github.com/nitra-1/AlphaGrpah (main branch)
- JDK 17 at D:\java runs Gradle itself; Gradle 8.11 at D:\gradle; GRADLE_USER_HOME set to D:\gradle-home (space-free path)
- The project's Java 21 toolchain is auto-provisioned by Gradle (foojay-resolver-convention in settings.gradle.kts) into D:\gradle-home\jdks - no manual JDK 21 install needed
- Windows quirk: this machine's username has a space in it, which breaks the JDK's internal loopback socket (java.nio Selector) unless JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/tmp is set. Already set as a permanent user env var, so new shells pick it up automatically
- A standalone local PostgreSQL 17 test instance runs on port 5434 (data dir D:\pgdata, role alphagraph_app / alphagraph_local_dev, db "alphagraph") - separate from docker-compose.yml's port-5432 setup, used only for manual verification. This machine also has a *shared* postgresql-x64-17 Windows service (data dir D:\Program Files\PostgreSQL\17\data, port 5433) that other local projects depend on - never reconfigure or repoint that service for this project; D:\pgdata is started independently instead: `pg_ctl -D D:\pgdata -o "-p 5434" -l D:\pgdata\startup.log start` (no admin rights needed - D:\pgdata is owned by the regular user account, unlike the shared service's data dir)
- Build/test/verify command: cd E:\Alpha && JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/tmp ./gradlew build

What we're building is not an application. We're building a financial intelligence platform. Those platforms almost always fail when teams jump straight into UI and dashboards. Bloomberg, FactSet, Capital IQ, and TradingView all spent years building their data and intelligence layers before polishing the front end.

So let's treat AlphaGraph like an enterprise platform.

AlphaGraph Master Roadmap
                Phase 0
        Platform Foundation
                 │
                 ▼
                Phase 1
      Intelligence Foundation
                 │
                 ▼
                Phase 2
      Corporate Intelligence
                 │
                 ▼
                Phase 3
      Decision Intelligence
                 │
                 ▼
                Phase 4
      Learning Intelligence

Each phase must produce a working product.

Phase 0 — Platform Foundation (Weeks 1–3)
Goal

Build the operating system for AlphaGraph.

No AI.

No dashboards.

No stock recommendations.

Just build the foundation correctly.

Module 0.1 — Solution Architecture

Deliverables

System Architecture
Module Architecture
Engine Architecture
Database Architecture
Deployment Architecture
Security Architecture

Artifacts

docs/

001_System_Architecture.md
002_Engine_Architecture.md
003_Database_Architecture.md
004_API_Architecture.md
005_Deployment.md
Module 0.2 — Technology Stack

Backend

Java 21

Spring Boot 3.5

Spring Batch

Spring Scheduler

Spring AI (future)

Python — NLP/ML sidecar service, stateless, called over REST from `corporate` (Phase 2) and `learning` (Phase 4); owns no data, all persistence stays in PostgreSQL via Java modules

PostgreSQL

Redis

Apache Kafka (future)

Flyway

JPA

MapStruct

Lombok

Frontend

React

TypeScript

Material UI

Recharts

AG Grid

Infrastructure

Docker

Docker Compose

GitHub Actions

Prometheus

Grafana

Cloud can wait.

Module 0.3 — Project Structure

I recommend a modular monolith.

alphagraph

common

reference

market

financial

ownership

corporate

sector

technical

risk

intelligence

decision

learning

api

scheduler

web

Each module later becomes a microservice if required.

Module 0.4 — Database

Deliverables

Flyway

Schemas

Tables

Indexes

Constraints

Seed Data

Not business logic.

Only structure.

Module 0.5 — ETL Framework

Generic ingestion pipeline.

Collector

↓

Parser

↓

Validator

↓

Normalizer

↓

Loader

Every source uses the same pattern.

Module 0.6 — Data Quality Engine

Every imported file gets

Quality Score

Completeness

Duplicates

Missing Fields

Validation Errors

Otherwise AI learns garbage.

Module 0.7 — Rule Engine

This becomes the heart.

Instead of

if(rsi>60)

Store

Rule

Threshold

Operator

Weight

Everything configurable.

Module 0.8 — Scheduler
6 PM

↓

Download

↓

Validate

↓

Process

↓

Calculate

↓

Score

↓

Notify

No manual execution.

Module 0.9 — API Layer
REST

OpenAPI

Swagger

JWT

Versioning
Module 0.10 — Logging

Every pipeline

Execution

Rows

Duration

Failures

Retry

Status
Phase 0 Deliverable

Running system.

Scheduler

↓

Imports dummy data

↓

Stores

↓

REST API returns data

No UI.

Phase 1 — Intelligence Foundation

This becomes your first usable product.

Data Sources

Market

OHLC

Volume

Delivery

Market Cap

Fundamentals

Sales

PAT

EPS

ROE

ROCE

Margins

Cash Flow

Ownership

Promoter

FII

DII

MF

Corporate

Corporate Actions
Engines
Technical Engine

Produces

Trend

Momentum

Breakout

Volume

Stage

RS

Confidence
Fundamental Engine

Produces

Growth Score

Profitability Score

Quality Score

Valuation Score
Institutional Engine

Produces

Institutional Score

Accumulation

Distribution

Smart Money
Sector Engine

Produces

Sector Strength

Sector Rotation

Leaders

Laggards
Risk Engine

Produces

Fundamental Risk

Technical Risk

Ownership Risk

Valuation Risk
Scoring Engine

Produces

Swing Score

Long-Term Score

Risk Score

Opportunity Velocity
Decision Engine

Produces

Top Improving

Top Weakening

Top Sectors

Market Mood

Emerging Opportunities
Dashboard
Top 20 Swing

Top 20 Long Term

Sector Leaders

Market Mood

Risk Meter

This is AlphaGraph MVP.

Phase 2 — Corporate Intelligence

Now AI begins reading.

Sources

Orders

Results

Investor Presentation

Conference Calls

Exchange Filings

News

Engines

Corporate Event Engine

Order Book Engine

Management Commentary Engine

News Engine

Outputs

Catalyst Score

Management Confidence

Order Quality

Growth Visibility
Phase 3 — Decision Intelligence

Now AI starts behaving like an analyst.

Modules

Portfolio

Watchlist

Comparison

Daily Report

AI Analyst

Trade Journal

Outcome Tracking

Example

Why moved from Rank 18 to Rank 5?

↓

AI explains

↓

Evidence
Phase 4 — Learning Intelligence

Now AlphaGraph becomes self-improving.

Modules

Learning Engine

Pattern Mining

Probability Engine

Weight Optimizer

Capital Allocation

Portfolio Optimizer

Eventually

Opportunity

↓

Historical Similarity

↓

Probability

↓

Suggested Allocation