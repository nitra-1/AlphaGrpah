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

Phase 2 (Corporate Intelligence) in progress - reading unstructured documents instead of structured rows, per the roadmap's Sources/Engines/Outputs list.
Module 2.1 status: DONE (Document Collection Framework - the first Phase 2 module, proving the whole Document Pipeline shape (Download -> OCR/Parse -> Extract -> Chunk -> Entity Extraction -> Embeddings) end-to-end against one real source (Exchange Announcements) before later modules add the other 5 named sources (Quarterly Results, Investor Presentations, Conference Calls, Annual Reports, News). Source: NSE's real, live, free corporate-announcements feed (https://www.nseindia.com/api/corporate-announcements) - confirmed live: a two-step cookie handshake (GET a normal page first, replay its Set-Cookie values) gets past NSE's anti-bot layer, no CAPTCHA/JS challenge encountered. PDF attachments serve freely from nsearchives.nseindia.com with no session needed. Whole-market single fetch (from_date=to_date=today), same shape as ownership's bulk/block deals pipeline (Module 1.7) - most rows quarantine as untracked instruments, ours resolve. Bundled sample is 9 real rows fetched live during development: one real, current announcement per tracked instrument, plus a real WIPRO row proving the quarantine path. New infrastructure: nlp-sidecar/, a stateless Python FastAPI service (pdfplumber text extraction, sentence-transformers all-MiniLM-L6-v2 embeddings, spaCy en_core_web_sm entity extraction) that corporate calls directly over REST (docs/001_System_Architecture.md §5 - no intelligence-bridging needed, it's an external service call, not a cross-domain-module dependency). No OCR fallback yet - Tesseract isn't installed on this dev machine, so a scanned/image-only PDF is flagged NEEDS_OCR rather than silently producing empty chunks, a real disclosed gap. corporate.documents/document_chunks/document_entities schema added (V2 migration); document_chunks.embedding is a plain Postgres real[], not pgvector's vector type - pgvector isn't installed on this project's real local Postgres, and that install is shared with other local projects, so pulling in an unofficial precompiled Windows binary wasn't an acceptable risk; docker-compose's postgres image comment notes pgvector/pgvector as the upgrade path once available. Two real bugs found and fixed during live verification: (1) NlpSidecarClient's first multipart attempt (raw LinkedMultiValueMap) sent a request FastAPI reported as missing the file field entirely - fixed with MultipartBodyBuilder, the correct tool for per-part filename/content-type; (2) that fix revealed a second issue - Spring's default JDK HttpClient attempts an HTTP/2 cleartext ("h2c") upgrade on every request, which uvicorn's plain HTTP/1.1 server doesn't understand, corrupting the connection and failing every call with a bodyless 422 - fixed by forcing SimpleClientHttpRequestFactory for this one client. Real environment constraint hit and resolved without touching shared infrastructure: this machine's shared postgresql-x64-17 Windows service (used by other local projects) had its data directory changed away from this project's D:\pgdata between sessions - rather than reconfiguring the shared service, D:\pgdata now runs as its own independent instance on port 5434, started without admin rights since that data directory is owned by the regular user account, not the service account. Verified: 5 Java unit tests + 4 Python unit tests all pass; live end-to-end run against real Postgres + the real sidecar processed all 8 tracked instruments - 140 real chunks with 384-dim embeddings, 1,661 real spaCy-extracted entities, WIPRO correctly quarantined. One disclosed data-quality gap found in real data: BEML's PDF has a Kannada-script signature block that didn't extract cleanly (garbled CID codes) - a genuine pdfplumber/font-encoding limitation on the source PDF, not fixed here)

Module 2.2 (Document Processing Pipeline) SKIPPED - user-confirmed after review found its named stages (Download -> Store -> Extract -> OCR -> Chunk -> Extract Entities -> Store Knowledge) are exactly what Module 2.1 already built and shipped end-to-end; moved straight to Module 2.3 instead. Its stated principle ("Never parse documents directly during scoring") remains a live design constraint honored by Module 2.3 below.

Module 2.3 status: DONE (Corporate Event Engine - the first real Corporate Intelligence ENGINE, architecturally distinct from every Phase 1 engine: those score clean numeric metrics via deterministic common.rules.RuleSet threshold rules, this one classifies free text into events via genuine semantic understanding. Detects 13 named event types (Large Order, Capacity Expansion, New Plant, Acquisition, Merger, Joint Venture, PLI Approval, Patent, Export Approval, Government Contract, Debt Raising, Promoter Buying, Promoter Selling) from corporate.documents.extracted_text - never re-parses the original PDF. User explicitly chose "Real LLM call (Claude API)" over rule-based keyword matching or a hybrid, as a genuine architectural trade-off. Uses the official com.anthropic:anthropic-java SDK (2.52.0), structured outputs (output_config.format with a hand-built JSON schema, not reflection-derived) against Claude Sonnet 5 - downgraded from the skill's default Opus 5 after an explicit cost/quality discussion with the user (this is a bounded classification task against a fixed schema, not open-ended reasoning, so a mid-tier model performs close to frontier-tier at ~40% lower cost; ~$0.02-0.04/document at this project's current tracked-instrument volume). CorporateEventEngine deliberately does NOT implement common.engine.Engine<I, O extends Score> - that contract assumes exactly one deterministic Score per input; this engine produces zero-or-more events per document from real classification, so forcing the Score/RuleSet shape would misrepresent what it does. AnthropicClient is injected via a small @Configuration bean (AnthropicClientConfig) rather than constructed inline in the engine's constructor - confirmed empirically (a standalone probe program against the real SDK jars) that AnthropicOkHttpClient.fromEnv() does NOT throw when ANTHROPIC_API_KEY is unset (credential resolution is deferred to request time), so this was purely a testability improvement, not a startup-safety fix. corporate.corporate_events schema added (V3 migration) - event_type/revenue_impact/signal are CHECK-constrained enums, but category is deliberately plain varchar (not enum-constrained): the user's own worked example shows only one value ("Revenue Positive"), so constraining it to a fixed taxonomy would mean guessing categories nobody has specified yet. DocumentStatus gained EVENTS_EXTRACTED, reached whether or not any events were actually found - "this document describes no classifiable event" is a real, final outcome (most routine filings match none of the 13 categories), not a reason to retry. Verified: 7 Java unit tests pass (JSON parsing, empty-events-is-valid, malformed-JSON handling, out-of-schema-enum rejection, out-of-range-confidence rejection, prompt/schema construction); V3 migration applied cleanly against real Postgres (confirmed via a temporary bootstrap test - schema shape, updated CHECK constraint, and Flyway history all verified, then reverted). Live end-to-end run against the real Claude API (Sonnet 5) processed all 8 tracked instruments' already-PROCESSED documents: 6 correctly produced zero events (routine filings, the expected "no event" outcome - proof the engine discriminates rather than force-fitting a category onto everything), INFY produced 2 real events (Large Order Win 88% confidence, Acquisition/Inorganic Growth 85% confidence, both Positive signal), and a purpose-built BEL test document ("received a Rs 2,800 Cr order from the Ministry of Defence... over the next 3 years") was correctly classified as both LARGE_ORDER (92% confidence, Revenue Positive, High impact, Positive signal) and GOVERNMENT_CONTRACT (75% confidence) - closely matching the user's own original worked example. One real environment hiccup during verification, unrelated to this module's code: the account's newly-added API credit balance took time to post on Anthropic's side (a genuine billing-platform delay, resolved by the user via their own billing page, not a code issue) - the first live attempt correctly surfaced Anthropic's real "credit balance too low" error rather than failing silently or being misinterpreted as a bug.

Architecture retrofit (between Module 2.3 and Module 2.4): user identified a real weakness before starting Module 2.4 (Order Book Engine) - if every Corporate Intelligence engine calls Claude directly on raw document text (as Module 2.3 originally did), adding a second engine means 2x LLM cost per document, 2x latency, and a real risk of two engines extracting inconsistent values for the same fact (e.g. one engine reading an order as ₹1,250 Cr, another reading ₹1,200 Cr from the same text). User proposed - and this project adopted - a Document Intelligence Engine: ONE canonical Claude call per document producing structured facts/topics/summary, stored once, read by every downstream engine as a deterministic rule engine. User explicitly chose the more disruptive "full retrofit now" option over building the new foundation alongside the old pattern, accepting that this meant rewriting Module 2.3's already-shipped, already-verified CorporateEventEngine.

Module 2.2 (revived) status: DONE (Document Intelligence & Knowledge Extraction Engine, corporate.knowledge package - the ONLY class in the corporate module that calls Claude now. One structured-output call per document (Claude Sonnet 5, same cost/quality reasoning as Module 2.3) producing documentType/sentiment/confidence/summary, open-ended topic tags, and a bag of business facts as key-value-unit triples (not a nested object with dynamic keys - JSON Schema's additionalProperties:false needs an enumerable property set, which a genuinely open-ended fact bag can't satisfy). The prompt asks the model to tag a document with the EXACT 13 corporate-event category names as topics whenever they genuinely apply, and to use a specific fact-key vocabulary (customer, orderValue, businessUnit, executionStart/End, orderScope, orderSector, orderRecurrence, orderLifecycleStage) for order-related documents - this is what lets every downstream rule engine stay simple and deterministic while the real semantic understanding still happens via one LLM call, just moved upstream. fact_type is normalized (lowercased, non-alphanumeric stripped) at write time so a lookup by a known key survives minor LLM key-naming variance ("Order Value" vs "order_value") - a real, disclosed limitation of a free-form fact vocabulary, not a fully closed enum. New schema (V4 migration): corporate.document_facts/document_topics/document_summary, plus corporate.document_consumer_checkpoints - a generic per-consumer idempotency table, since document status can no longer mean "has engine X looked at this yet" once multiple independent engines read the same KNOWLEDGE_EXTRACTED document. DocumentStatus's EVENTS_EXTRACTED (Module 2.3's original value) was removed and replaced with KNOWLEDGE_EXTRACTED; documents previously EVENTS_EXTRACTED were reset to PROCESSED by the migration so they flow through the new pipeline (their old corporate_events rows were left in place as genuine historical output, not deleted).

Module 2.3 retrofit status: DONE (CorporateEventEngine is now a deterministic rule engine, not an LLM caller - it exact-matches a document's canonical topics against the 13 category names, deriving category/expectedDuration/revenueImpact/signal from static lookups and extracted facts rather than a bespoke per-document LLM synthesis. This is a real, accepted quality trade-off against the original per-document classification, in exchange for one shared, cheaper, consistent extraction pass. Still deliberately does not implement common.engine.Engine<I, O extends Score> - topic-matching classification doesn't fit that contract any better than the original LLM version did. RULE_VERSION replaces PROMPT_VERSION as the reproducibility-tracking concept (same corporate_events.prompt_version column). Idempotency moved from a document-status transition to a document_consumer_checkpoints row (consumer="CORPORATE_EVENT_ENGINE").

Module 2.4 status: DONE (Order Book Engine, corporate.orderbook package - one of the most valuable engines per the user's framing. Two halves: (1) OrderBookLedgerParser reads canonical facts for documents carrying an orderlifecyclestage fact (NEW_ORDER/TENDER_WIN/EXECUTION_UPDATE/CANCELLATION/COMPLETION) into corporate.order_book_ledger rows (V5 migration) - a document with no such fact simply adds no row, the same "zero is a valid outcome" precedent as corporate_events; (2) OrderBookAggregationEngine is a genuine common.engine.Engine<I, O extends Score> implementation (unlike Module 2.3) - it sums an instrument's ledger into numeric metrics (current order book value, growth %, execution visibility years, order count) and bands them into an OrderQuality (EXCELLENT/GOOD/FAIR/POOR) via common.rules.RuleSet threshold rules seeded in common/V8, exactly like every Phase 1 engine. Real, disclosed simplification: without a stable order-reference number in the extracted facts, there's no reliable way to match a later COMPLETION/CANCELLATION back to the specific order it closes out, so the ledger is treated as a plain net debits/credits account, not matched order pairs. orderBookGrowthPct is genuinely null (not fabricated as 0%) on an instrument's first-ever snapshot. Signals (corporate.order_book_signals, replaced wholesale on every run rather than appended, since they reflect current state): LARGE_ORDER (>=500 Cr), REPEAT_CUSTOMER (simple case-insensitive/trimmed customer-name match - a real disclosed limitation, not fuzzy matching), EXECUTION_DELAY (an active order's execution-end year has passed), ORDER_CANCELLATION. MARGIN_IMPROVING (named in the roadmap) is deliberately NOT modeled - no margin data is extracted anywhere in this pipeline yet, and deriving it from order-value trends alone would be a real stretch, not a genuine signal - same disclosed-gap pattern as Risk Engine's absent Event Risk domain (Module 1.9). Verified: 8 DocumentIntelligenceEngineTest + 8 CorporateEventEngineTest (rule-based) + 5 OrderBookLedgerParserTest + 7 OrderBookAggregationEngineTest + 7 OrderBookSignalDetectorTest, all passing; V4/V5 (corporate) and V8 (common) migrations applied cleanly against real Postgres. Live end-to-end run against the real Claude API reprocessed all 8 real tracked-instrument documents through the full new pipeline (Knowledge Extraction -> Event classification -> Order Book) - genuinely rich canonical facts/topics extracted per document (ESG ratings, credit ratings, CEO transitions, disciplinary actions, ESOP allotments, postal ballots - far richer than Module 2.3's original narrow 13-category-only extraction), BEML and INFY correctly re-detected real events (DEBT_RAISING, ACQUISITION) via the new rule-based engine, and the other 6 correctly produced zero events (none of their real content matches any of the 13 categories). None of those 8 real documents happened to describe an actual order, so a purpose-built BEL test document (₹2,800 Cr Ministry of Defence order, domestic, one-time, Electronics business unit, 2026-2029) was added to specifically exercise the Order Book path live: correctly extracted into the ledger (customer, ₹2,800 Cr, NEW_ORDER), aggregated into a snapshot (current order book ₹2,800 Cr, 3-year execution visibility, FAIR quality - correctly computed from the seeded rule thresholds), and flagged LARGE_ORDER.

Three-stage extraction retrofit (before Module 2.5) status: DONE. User identified a real architectural risk before Module 2.5: Module 2.2's single shared prompt already covered both general classification AND order-specific fact extraction (Module 2.4); adding Management Commentary's guidance/margin/demand/competition vocabulary to the SAME prompt would mean one prompt covering three unrelated domains at once, and every future engine (News, Patents, ...) would keep diluting it further. Retrofitted to a formal three-stage pipeline instead: Stage 1 (Document Understanding, lean) -> Routing -> Stage 2 (Specialized Extraction, one focused Claude call per domain) -> Stage 3 (Canonical Facts, same document_facts table regardless of which extractor wrote a row). User explicitly chose to retrofit Module 2.4 immediately rather than let two competing document-processing patterns coexist ("that split will eventually force a rewrite anyway").

New shared abstractions (corporate.knowledge): DocumentClassification (Stage 1's output - documentType/topics/entities/summary/sentiment/confidence/recommendedExtractors), DocumentContext (what a Stage 2 extractor receives), ExtractedFact (now carries a second, qualitative confidence dimension - commitmentLevel LOW/MEDIUM/HIGH/VERY_HIGH from language strength, e.g. "we hope" vs "we expect" vs "orders already secured" - distinct from the existing 0-100 extractionConfidence; nullable, since only forward-looking statements genuinely have a hedging dimension), and the DocumentExtractor interface itself (supports(classification) / extract(context)). DocumentRouter auto-discovers every Spring-registered DocumentExtractor bean and dispatches to whichever ones return supports()=true - adding a future extractor (PatentExtractor, NewsExtractor, ...) never requires touching the router or any existing extractor, only a new @Component. DocumentIntelligenceEngine (Stage 1) dropped all fact extraction - it now only classifies/tags/summarizes and recommends which Stage 2 extractors should run (recommendedExtractors is Stage 1's own routing signal, a plain string list an extractor's supports() checks against, e.g. "ORDER").

Order-fact extraction (Module 2.4's original single-prompt design) migrated into a new OrderExtractor (Stage 2) with its own narrow prompt covering only customer/orderValue/businessUnit/executionStart/executionMonths/orderScope/orderSector/orderRecurrence/orderLifecycleStage - "nothing else", per the user's design. Real improvement made in the migration: instead of asking the model for two independently-guessed years (executionStart and executionEnd), it now asks for a start year plus a duration in months, and OrderExtractor computes the end year deterministically in Java - more reliable than hoping two independent LLM guesses stay self-consistent. Normalizes into the EXACT SAME document_facts key vocabulary the original shared prompt used, so corporate.orderbook.OrderBookLedgerParser (Module 2.4) needed ZERO code changes - proof the canonical-facts abstraction genuinely decouples writers from readers. V6 migration adds document_facts.commitment_level (nullable, for the second confidence dimension - not yet populated by any extractor since Management Commentary doesn't exist yet). Verified: 8 DocumentIntelligenceEngineTest (rewritten for the lean Stage 1 shape) + 9 OrderExtractorTest + 4 DocumentRouterTest, all passing; V6 migration applied cleanly against real Postgres. Live end-to-end run against the real Claude API on a purpose-built order document confirmed the full chain: Stage 1 correctly tagged topics ("Large Order", "Government Contract") and recommended the ORDER extractor; Stage 2's OrderExtractor ran its own separate real Claude call and correctly extracted every field, including executionEnd=2029 computed from a real executionStart=2026/executionMonths=30 pair (2.5 years rounds up to 3); Module 2.4's ledger/snapshot/quality scoring worked unchanged, reading facts from the new extractor exactly as it read facts from the old shared prompt.

Module 2.5 status: DONE (Management Commentary Engine, corporate.commentary package + corporate.knowledge.ManagementExtractor). Stage 2: ManagementExtractor implements DocumentExtractor, supports() triggers on Stage 1 recommending "MANAGEMENT", and its prompt is scoped to exactly 9 metric types (REVENUE_GUIDANCE, MARGIN_GUIDANCE, CAPEX, DEMAND, PRICING, COMPETITION, HIRING, EXPORTS, RISK) - richer than sentiment, per the user's original framing ("sentiment isn't enough"). Unlike OrderExtractor's at-most-one-order assumption, a single document can carry several distinct forward-looking statements at once, so each becomes its own fact_group (a new nullable UUID column on document_facts, V7 migration) - a downstream reader groups a document's facts back into individual statements via fact_group rather than assuming one statement per document. Populates the second confidence dimension the three-stage retrofit added but left unused: commitmentLevel (LOW/MEDIUM/HIGH/VERY_HIGH, from the language's actual strength - "we hope" vs "we expect" vs "we are confident" vs "orders already secured"), stored on the group's metrictype fact only (redundant to repeat on every fact in a group). Two-layer output, mirroring Order Book's shape: Layer 1 (ManagementObservationParser + management_observations table, V7) - one immutable row per document per statement, reassembled from a fact_group's facts, skipped if the group is missing metrictype or direction; Layer 2 (ManagementCommentaryEngine, a genuine common.engine.Engine<I, O extends Score> implementation like Order Book, unlike Module 2.3) - filters an instrument's observation history to REVENUE_GUIDANCE only, derives directionSignal/commitmentStrength/persistenceQuarters (consecutive positive quarters from newest) as RuleSet inputs (rules seeded in common/V9, same 3.0-best-case/-3.0-worst-case weighting convention as every prior engine), and produces growthVisibilityScore + guidanceTrend (UPGRADING/STABLE/DOWNGRADING, from comparing the latest two numeric observations - UNKNOWN with fewer than two) + managementCredibility (HIGH needs persistence>=3 AND commitment>=2; LOW needs persistence==0 OR commitment==0; else MEDIUM). Real, disclosed scoping decision: only REVENUE_GUIDANCE feeds the numeric score/trend for now - margin/capex/demand/pricing/competition/hiring/exports/risk observations are captured and stored (available for future engines or direct display) but not yet aggregated into a second scored dimension, since the user's spec named Growth Visibility as the one derived score for this module. Verified: 79 corporate-module unit tests passing (9 ManagementExtractorTest + 5 ManagementObservationParserTest + 8 ManagementCommentaryEngineTest, plus all pre-existing tests updated for DocumentFact's now-9-arg constructor), full project build green. One real bug fixed during unit testing: ManagementExtractor's prompt template used Java text-block .formatted() with literal illustrative "%" characters (e.g. "30%") that .formatted() misread as invalid format specifiers, throwing UnknownFormatConversionException - fixed by escaping to "%%", leaving the genuine %s substitution untouched. Live end-to-end verification against the real Claude API and real Postgres (temporary ManualVerificationTest in bootstrap, reverted after): a purpose-built TCS earnings-call-transcript document ("we expect 30% revenue growth over the next two years... this is not a hope... operating margin improving by 100 basis points... approximately 500 crore capex... strong domestic demand... 10,000 freshers... increased competitive intensity in exports... confident in our ability to compete on value") was correctly parsed by the real model into all 8 distinct statements with sensible metric types, directions, and commitment levels (REVENUE_GUIDANCE/PRICING/RISK correctly scored HIGH commitment for confident/factual language, the rest MEDIUM for plain expectation language) - none fabricated, none merged. The engine correctly scored growthVisibilityScore=70.0 (1 REVENUE_GUIDANCE observation: +1.5 direction, +0.5 commitment, 0 persistence since only 1 quarter of history -> sum 2.0/3.0 -> 50+20=70), guidanceTrend=UNKNOWN (correctly, since trend needs 2+ numeric observations and this instrument only had 1), and managementCredibility=MEDIUM (persistence=1 and commitment=2 satisfy neither the HIGH nor LOW threshold). All temporary rows cleaned up and confirmed zero residue via direct psql query after the test.

Module 2.6 status: DONE (News Intelligence Engine - the last of Phase 2's four planned engines, corporate.knowledge.NewsExtractor + corporate.news + corporate.newsfeed packages). Broke a real assumption every prior source relied on: corporate.documents.instrument_id/symbol were NOT NULL because every source until now (exchange filings, earnings calls) genuinely belongs to exactly one company at collection time. A news item ("Government announces Semiconductor PLI") doesn't - it affects zero, one, or many companies, determined only after AI reads it. V8 migration makes instrument_id/symbol nullable (NULL only for NEWS-sourced rows) and widens external_id from varchar(50) to varchar(500) (RSS GUIDs are full article URLs, not NSE's short seq_id). Two scoping decisions made explicitly before building (AskUserQuestion): (1) nullable instrument_id + a new document_instrument_links join table, rather than denormalizing one document row per affected company; (2) score/link only AlphaGraph's tracked instrument universe (currently 8 names) - untracked companies a news item mentions are extracted but produce no link/score, a real and expected outcome, not an error.

Stage 2: NewsExtractor implements DocumentExtractor (supports() triggers on Stage 1 recommending "NEWS", a new routing identifier added to Stage 1's prompt alongside ORDER/MANAGEMENT), deliberately does NOT know AlphaGraph's tracked universe - it names companies freely from the text, exactly as OrderExtractor/ManagementExtractor stay narrow to their own domain. Each affected company becomes its own fact_group (same multi-statement-per-document pattern as ManagementExtractor). Universe-matching is a separate, deterministic step: NewsInstrumentMatcher (corporate.news) resolves a free-text company name against reference.instruments by normalized name/symbol matching after stripping corporate suffixes (Ltd/Limited/Pvt/Inc) - a real, disclosed limitation (not fuzzy or embedding-based; a genuine misspelling won't match), same class of simplification as Order Book's REPEAT_CUSTOMER signal. Keeping extraction and universe-matching apart means NewsExtractor's output stays valid even if the tracked universe grows later - no re-extraction needed.

Two-layer output (corporate.news), mirroring Order Book/Management Commentary: Layer 1 (NewsLinkParser + document_instrument_links, V8) - one immutable row per (document, resolved company) link, groups with an unresolved company name are silently dropped (the concrete implementation of "tracked instruments only"). Layer 2 (NewsCatalystEngine, a genuine common.engine.Engine<I, O extends Score> implementation) - unlike Management Commentary's quarterly-cadence guidance, news catalysts are episodic, so there's no persistence analogue; the three RuleSet inputs are net direction across an instrument's full link history, catalyst volume (independent links reinforcing the same direction), and recency in days since the most recent link (rules seeded in common/V10, same 3.0-best-case/-3.0-worst-case convention). Produces catalystScore + catalystTrend (POSITIVE/NEGATIVE/MIXED/NONE) + recentCatalystCount.

Live RSS collector built as part of this module (user's explicit choice over a sample-dataset-first approach): corporate.newsfeed package, same Collector/Parser/Validator/Normalizer/Loader ETL shape as every prior source. Three feeds verified genuinely live via direct curl before wiring in (WebFetch itself was blocked for these domains): Economic Times Markets and LiveMint Markets (both returned real, current-dated items), and PIB English press releases (real and current, but a real format gap - only title/link per item, no description/pubDate, unlike ET/LiveMint's fuller items). Two candidates were tried and dropped as genuinely unusable, disclosed rather than silently worked around: Moneycontrol's RSS endpoints all return HTTP 200 but every item is frozen at April 2024 (a dead feed masquerading as live); Business Standard returns HTTP 403 (Akamai bot-block). NewsFeedLoader goes straight to PROCESSED status with no download/OCR stage - RSS articles arrive as text directly, and nothing downstream reads document_chunks/document_entities regardless of source (confirmed via grep - only Module 2.1's writers exist, no readers anywhere), so chunking a short news article would be pure overhead. Two dedup layers: (source, external_id) natural upsert key as always, plus a real, disclosed simple cross-outlet dedup (exact match on the article title with punctuation/whitespace/case stripped, within a 48h window) - not semantic/embedding-based, since nothing currently populates document_chunks' embeddings for this fast non-chunked path.

Verified: 114 corporate-module unit tests passing (35 new: NewsExtractorTest, NewsLinkParserTest, NewsCatalystEngineTest, NewsFeedParserTest, NewsFeedNormalizerTest, RssFeedCollectorTest), full project build green. Live end-to-end verification against the real Claude API and real Postgres (temporary ManualVerificationTest in bootstrap, reverted after) in two parts: Part A ran the actual production NewsFeedScheduledPipeline against the real live feeds via the real PipelineRunner - 105 real news items collected from Economic Times/LiveMint/PIB in one run, all correctly landing as PROCESSED with instrument_id NULL; left in place afterward as genuine collected data, not test scaffolding (matching Module 2.4's precedent of leaving real reprocessed documents' output in place). Part B ran a purpose-built government-policy news item naming Reliance Industries (green hydrogen scheme beneficiary), Tata Consultancy Services (a new government IT contract), and Kaynes Technology (an untracked company) through the full pipeline: Stage 1 correctly recommended BOTH "NEWS" and "ORDER" extractors on the same document (TCS's contract win is genuinely both a news impact and an order) - the first live proof that DocumentRouter correctly dispatches to multiple qualifying Stage 2 extractors on one document, not exercised by any single-domain test document before. NewsExtractor correctly identified all three companies with sensible directions/signals; NewsInstrumentMatcher correctly resolved Reliance Industries -> RELIANCE and Tata Consultancy Services -> TCS while correctly dropping Kaynes Technology (untracked) - exactly the scoping decision working live on a genuinely ambiguous real-world example. NewsCatalystEngine computed catalystScore=45.0 for both instruments, matching the hand-calculated expected value exactly (1 link: direction signal not fired since net=1 doesn't cross the +/-2 threshold, volume<=1 fires -1.0, recency=0 days fires +0.5, raw=-0.5 -> 50-5=45) with catalystTrend=POSITIVE and confidence=60.0. All temporary rows cleaned up and confirmed zero residue via direct psql query after the test; the 105 real RSS-collected documents and their real live-verification proof were confirmed still present.

Phase 2 (Corporate Intelligence)'s original scope (2.1-2.6) completed with News (above). The user then extended Phase 2 with a further roadmap (2.7 Knowledge Graph, 2.8 Corporate Signal Engine, 2.9 AI Analyst, 2.10 Dashboard) rather than moving to Phase 3 immediately - the "connecting everything, not isolated events" reasoning layer the individual engines were always meant to feed.

Module 2.7 status: DONE (Knowledge Relationship Engine - explicitly NOT a graph database. User's direction, verbatim: "the graph is not the database, the graph is the semantic model" - at AlphaGraph's scale the real workload is 2-4 hop traversals (which companies benefit from a scheme, which competitors also serve a customer), well within indexed PostgreSQL tables + recursive-CTE-style joins, and a second persistence technology (two data models, ETL sync, a second query language) buys nothing here. Two tables carry the whole model (new `knowledge` schema, corporate/V9): `entity_master` (id, entity_type, canonical_name, aliases, status, linked_instrument_id, linked_sector_id - entity_type deliberately broader than the tracked universe: COMPANY/CUSTOMER/THEME/GOVERNMENT_SCHEME/COMPETITOR/SECTOR) and `relationship` (from_entity_id, relationship_type, to_entity_id, source_document_id, confidence, created_by_engine - relationship_type a controlled vocabulary of 13 values, no free text ever). Seeded at migration time: all 8 tracked instruments become COMPANY entities, all 6 sectors become SECTOR entities, BELONGS_TO_SECTOR edges connect them from real reference data.

corporate.relationships package: EntityResolver (public, the only thing that ever turns free text into an entity_id) is role-agnostic by design - resolution always searches every entity_type for a name match first, and only uses the caller's suggested type when actually creating a new entity, so a name first seen as a COMPETITOR mention and later as a genuine COMPANY never splits into two rows. EntityNameNormalizer was extracted from corporate.news.NewsInstrumentMatcher (Module 2.6) so both share one matching implementation instead of two copies of the same regex; NewsInstrumentMatcher itself was retrofitted to read from entity_master (WHERE linked_instrument_id IS NOT NULL) instead of reference.instruments directly, with zero change to its tracked-only, non-creating semantics. RelationshipBuilder is the graph's only writer, called synchronously (not a real event bus - Kafka remains "future" everywhere else in this project) from KnowledgeExtractionOrchestrator right after Stage 2 facts are written - extractors emit canonical facts, RelationshipBuilder resolves and connects them, exactly the separation the user's design specified. CompetitorGroupExpander seeds one real curated group (EMS: Kaynes/Syrma SGS/Dixon/Avalon/PG Electroplast - none of them tracked instruments, deliberately, since competitors are never inferred dynamically from free text) and expands it into pairwise COMPETES_WITH edges.

Retrofit scope (user's explicit choice: "Retrofit 2.4/2.5/2.6 to use entity_id", the more invasive of two options offered) turned out to differ per engine once grounded in the actual shipped schema: Order Book's `order_book_ledger.customer` (free text) became `customer_entity_id` (V9 migration, real bugfix bonus - REPEAT_CUSTOMER detection now compares entity_id equality instead of case-insensitive string matching, genuinely fixing a limitation Module 2.4 had disclosed); News and Management Commentary's own tables needed no schema change (document_instrument_links.instrument_id was already a real FK; management_observations never stored a second free-text entity reference), so their retrofit was extending NewsExtractor/ManagementExtractor's Stage 2 JSON schema with optional relatedEntityName/relatedEntityType/relationshipType fields per fact group - since a deterministic rule can't infer "BENEFICIARY_OF" from prose, the LLM has to say it. Order facts (which have no fact_group, per OrderExtractor's at-most-one-order design) get a fixed EXECUTES_FOR edge from the reporting company to its customer, since that semantic never varies and doesn't need an LLM-supplied relationship type.

Verified: 129 corporate-module unit tests passing (RelationshipBuilderTest covers the routing logic - News-shaped groups vs Management-shaped groups falling back to the document's own symbol vs ungrouped Order facts vs malformed/incomplete LLM output skipped without throwing; EntityResolver/RelationshipWriter/CompetitorGroupExpander are thin JDBC wrappers left to live verification, same convention as every other Reader/Writer/Store/Matcher in this module, none of which have ever had dedicated unit tests). Live end-to-end verification against real Postgres and the real Claude API turned into a genuinely large-scale proof: a purpose-built compound TCS document (an order win + management guidance entering a new theme + a separate government-scheme item naming an untracked company) was run through the full pipeline, and per user's explicit choice, `extractAllPending()` was allowed to sweep its real, natural target - the entire 105-document real news backlog Module 2.6 had left in PROCESSED status, not just the one test document. Genuine findings from doing it this way: (1) a real pre-existing bug surfaced and was fixed - `document_summary.document_type` was varchar(50) but was never actually constrained to a short enum in Stage 1's JSON schema, and a compound multi-signal document produced a longer, genuinely descriptive label that exceeded it (V10 migration widens to varchar(200)); (2) 79 of 86 pending documents succeeded, the 7 failures were 100% pre-existing and unrelated to this module (four real Anthropic API 529 overload responses, two documents where the model emitted a degenerate/looping numeric token in place of a confidence value, one caught by an existing out-of-range guard) - correctly caught and logged per-document by the existing try/catch, not a Module 2.7 regression; (3) the test document's News-shaped and Management-shaped fact groups both independently identified the SAME real-world "TCS entering Semiconductor Design Services" relationship, and RelationshipWriter's ON CONFLICT DO NOTHING correctly collapsed them into one edge instead of two - unplanned, genuine proof that cross-extractor redundancy doesn't corrupt the graph; (4) beyond the test document, the real backlog independently produced 12 genuine relationship edges (7 PART_OF_THEME, 4 AFFECTED_BY, 1 CUSTOMER_OF) and 9 new real company/theme/customer entities from actual news content, not synthetic test data. Final state: entity_master has 17 COMPANY/6 SECTOR/5 THEME/4 COMPETITOR/1 CUSTOMER rows; relationship has 20 COMPETES_WITH/8 BELONGS_TO_SECTOR/7 PART_OF_THEME/4 AFFECTED_BY/1 CUSTOMER_OF; 98 of the 105 real news documents reached KNOWLEDGE_EXTRACTED (7 remain PROCESSED for a future scheduled run to retry, exactly as the checkpoint-based idempotency design intends). All test-specific data (including an orphaned document from an earlier interrupted verification attempt, discovered and cleaned via direct psql query) confirmed removed; the real backlog's genuine output was left in place.

Module 2.8 status: DONE (Corporate Signal Engine, corporate.signal package - combines the four already-built corporate engines into one Corporate Score, the roadmap's own worked example ("Order Win + Positive Guidance + Capacity Expansion + Strong Demand = Corporate Score") naming one ingredient from each domain. Deliberately narrower than Phase 1's originally-planned, never-built Scoring/Decision Engine (which would also blend in Technical/Fundamental/Institutional/Sector/Risk) - that remains separate, not-yet-requested future work; this module only combines corporate-domain signals.

New table `corporate.corporate_scores` (V11) carries the composite plus all four contributing domain scores alongside it (order_book_score/management_score/news_catalyst_score/event_net_signal, all nullable except the last) - not read by anything yet, but Module 2.9's AI Analyst is explicitly designed to explain, not calculate, and explaining "why did the Corporate Score improve" needs to know which domain moved, not just the final number. Real gap found and closed before this could be built: three of the four upstream Layer 2 tables (order_book_snapshots, management_commentary_snapshots, news_catalyst_scores) only ever had write-only or internal-only stores - OrderBookSnapshotReader/ManagementSnapshotReader/NewsCatalystSnapshotReader (all new, public, `findLatest(instrumentId)`) and CorporateEventReader (new, public, `findRecentByInstrument` - corporate_events has no as_of_date, so "recent" needed an explicit 90-day lookback window rather than a single latest row) are the first consumers of any of the four.

CorporateSignalEngine is a genuine common.engine.Engine<I, O extends Score> implementation, same RuleSet-driven shape as every other Score-implementing engine (rules seeded in common/V11: Order Book and Management weighted equally highest at 0.75 each - the two richest, most-frequently-updated domains; Corporate Events next at 1.0 - concrete/factual but more binary; News Catalyst lowest at 0.5 - most episodic/noisy, per its own engine's design commentary; best-case raw sum 3.0 -> score 80, worst-case -3.0 -> score 20, the same convention every engine uses). Missing domains contribute 0 rather than being penalized (the same null-tolerant pattern intelligence.risk.RiskAnalysisOrchestrator established) - confidence separately scales with how many of the four domains actually have evidence (40 baseline + 15 per domain present, so a score resting on all four domains reads as far more trustworthy than one resting on just one). CorporateSignalOrchestrator's driving set is the union of instrument_ids across all four upstream tables, not all 8 tracked instruments unconditionally - an instrument with zero corporate history anywhere would only ever produce a meaningless neutral 50, noise rather than signal, so it's simply not computed.

Verified: 136 corporate-module unit tests passing (CorporateSignalEngineTest covers all-four-domains-strong/weak, single-domain confidence scaling, missing-domain null-tolerance, event-count-alone driving the score, rating band boundaries, and score clamping - mirrors the seeded common/V11 rules exactly). Live end-to-end verification needed no new Claude API calls at all - the engine is pure deterministic math over already-computed scores - so it instead surfaced and closed a real gap: three of the four upstream orchestrators (Order Book, Management Commentary, News Catalyst) had never actually been run against the 98-document real backlog Module 2.7 left in KNOWLEDGE_EXTRACTED status, so their Layer 2 tables were still empty. Running them for the first time was itself a legitimate production step (not test scaffolding), and produced genuine results: News Catalyst scored HDFCBANK/ICICIBANK at 45.0 each from real link history; Order Book and Management Commentary found no real order/guidance-shaped facts anywhere in the backlog (a real, honest outcome - most general news doesn't describe a company's own order win or forward guidance, not a bug). Corporate Signal Engine then correctly combined this real data with the 4 pre-existing real corporate_events (INFY/BEML, from Module 2.4's original verification): INFY's 2 net-positive events correctly scored 60.0/NEUTRAL/confidence 55 (hand-verified against the seeded rules exactly), BEML's 1 neutral-signal event correctly scored a flat 50.0 (net signal 0 doesn't cross either threshold), and HDFCBANK/ICICIBANK's 45.0 catalyst scores correctly stayed at 50.0 (not extreme enough to move off neutral) - all four real results matched hand-calculated expected values exactly. No domain ever had all four inputs simultaneously in real data (the "all four strong" scenario from the roadmap's worked example is covered by unit tests instead), but the full driving-set query, all four readers, and the orchestration wiring were all proven end-to-end against genuine production data. Nothing to clean up - no synthetic data was inserted, only real backlog data processed through pipeline stages that should already have run.

Module 2.9 status: DONE (AI Analyst). Before building, ran a real audit of what the roadmap's own worked example ("Explain why BEL improved today" -> 5 bullets citing an order, "order book highest ever," raised guidance, sector strength, and revenue visibility) actually requires against the codebase as it stood - genuinely useful, since it found that most of the underlying data existed but the READ access needed to construct evidence mostly didn't: two readers existed but were package-private (OrderBookLedgerReader/ManagementObservationReader), and three capabilities were completely missing - any history/trend query for any of the four corporate engines (every store was write-only), a sector score reader (only a write-only SectorScoreWriter existed anywhere in the codebase), and any way to read the Module 2.7 knowledge graph back (only writes existed). Per the user's explicit choice, built the full evidence layer before the Analyst itself rather than shipping a narrower version first.

Scope, stated explicitly before building: Module 2.9 is "explain why this instrument's outlook changed," parameterized by instrument - not open-ended natural-language query routing (intent classification, entity extraction from free text), which is a materially bigger, separate task the spec doesn't actually demonstrate.

Real architectural correction made mid-build: the evidence layer genuinely needs to read across corporate AND sector domains (sector standing, "Defence sector remains strongest"), but corporate.build.gradle.kts depending on sector directly violates docs/001_System_Architecture.md §4 ("domain modules never depend on each other") - caught immediately by the existing ModuleBoundaryArchTest, not discovered later. Fixed by moving the entire analyst package into intelligence instead (which already legitimately depends on both corporate and sector, the same reason intelligence.risk already bridges financial/technical/ownership rather than living in any one of them) - the correct, established home for genuine cross-domain code, not a workaround.

Two-part design enforcing "AI explains, it never calculates" structurally, not just by prompt instruction: intelligence.analyst.AnalystEvidenceBuilder does every calculation deterministically in Java (day-over-day deltas, "highest ever" comparisons against full history, cross-sector ranking via a new SectorScoreReader.findAllLatest()) and produces a list of already-worded EvidenceFact records with every number already substituted in; intelligence.analyst.AiAnalystClient is the only Claude call in the package (and the first anywhere in this project whose output is free-form prose, not structured JSON, since the task is genuinely fluent narration) and its prompt explicitly forbids introducing any number not already present in the facts it's given - it is never handed anything it could calculate FROM (no raw historical series, no ledger), only the pre-verified conclusions. New readers added to close the gaps: OrderBookSnapshotReader.findHistory/ManagementSnapshotReader.findHistory (day-over-day/highest-ever), a new CorporateScoreReader (Module 2.8's store was write-only), a new SectorScoreReader (sector module, first reader ever - findLatest/findLatestForInstrument/findAllLatest), and a new RelationshipReader (corporate.relationships, first graph read capability - findOutgoing/findIncoming with both endpoints' names already resolved). AiAnalystService.explainScoreChange(instrumentId) is the module's only public entry point.

Verified: 136 corporate + 17 intelligence unit tests passing (AnalystEvidenceBuilderTest covers every fact-generation branch - score delta above/below reporting, highest-ever detection against real history including the single-snapshot edge case, customer-entity-name resolution, sector rank #1 vs #2 phrasing, positive/negative event and news filtering, graph relationship phrasing; AiAnalystClientTest proves the prompt's calculation-forbidding language and graceful-degradation instruction are actually present). Live end-to-end verification against real Postgres and the real Claude API used INFY's genuinely real data (2 real corporate_events from Module 2.4's original verification, IT sector's real 100.0 score from Module 1.8 - actually the strongest of the 4 real sectors on record) plus one synthetic "yesterday" corporate_scores row (45.0, clearly temporary, to exercise the real day-over-day comparison path against a genuine second row rather than fabricated narrative text). The real Claude call produced a genuinely accurate 5-bullet explanation - correctly cited the score move (45.0 -> 60.0), the real IT sector standing (100.0, "strongest"), and both real corporate events with their exact revenue-impact levels (LARGE_ORDER/HIGH, ACQUISITION/MEDIUM) - every number in the output traced directly to a supplied fact, with a closing synthesis sentence that added no new figures. Cleanup confirmed only the synthetic row was removed; INFY's real score untouched.

Module 2.10 status: DONE (Decision Intelligence API Layer - the user's own name for this module, the last of the extended Phase 2 roadmap). Grounded before building: confirmed via repo search that no frontend exists anywhere in this project (no web module, no npm/React project - the original roadmap's Frontend tech stack was never touched across 11+ prior modules), so per explicit user confirmation this module is new REST API endpoints (JSON, JWT-secured, matching the existing api module's pattern), not an actual UI - a real scope question worth asking rather than assuming, since "Dashboard" could plausibly have meant either.

Real new capability this module needed that nothing before it required: every widget ranks or lists data ACROSS the whole tracked universe ("Today's Biggest Orders" across all instruments, "Top Catalysts" ranked highest-first) - but every reader built in Modules 2.4-2.9 was single-instrument-scoped, since that's all their consumers (aggregation engines, the AI Analyst) ever needed. Added cross-instrument query methods to the existing corporate-module readers rather than building a new layer: OrderBookLedgerReader.findRecentAcrossAllInstruments, CorporateEventReader.findRecentAcrossAllInstruments, ManagementObservationReader.findRecentAcrossAllInstruments (all lookback-window-based, default 7 days except orders which default to 1 - "today," literally), NewsLinkReader (made public, new findRecentByDirection for Positive/Negative News), and findAllLatest() added to NewsCatalystSnapshotReader/ManagementSnapshotReader/CorporateScoreReader (one row per instrument, highest score first - the same DISTINCT ON + Java-side sort pattern Module 2.9's SectorScoreReader established, since Postgres's DISTINCT ON requires its own column as the first ORDER BY key, so ranking by a different column happens after the fetch).

New api.dashboard package (api module, which already depended on every domain/intelligence module): DashboardService assembles DTOs by calling the corporate-module readers directly (per docs/004_API_Architecture.md §5 - the api module consumes domain readers, it doesn't re-implement their queries) and converts each domain record into a dashboard-shaped DTO - notably resolving order-ledger customerEntityId into a real customer name via Module 2.7's EntityReader, never exposing a raw entity id to a client. DashboardController exposes one GET endpoint per named widget (/api/v1/dashboard/biggest-orders, /corporate-events, /guidance-changes, /positive-news, /negative-news, /top-catalysts, /growth-visibility, /corporate-scores) plus a combined /api/v1/dashboard summary endpoint for a single-call load - authenticated like every other read endpoint (any valid JWT, no role restriction), no new security surface introduced.

Verified: 136 corporate + 18 api + 17 intelligence unit tests passing (DashboardServiceTest covers every widget's DTO mapping, including the null-customer-name edge case and the positive/negative direction delegation). Live end-to-end verification was genuinely different from every prior module - since this is the first module that's actually HTTP-facing (every prior module's "live verification" called Java services directly), the real test was booting the actual Spring Boot application, logging in against the real seeded admin account to get a genuine JWT, and curling every endpoint over real HTTP. All 8 widget endpoints plus the combined summary returned correct, real JSON: corporate-events returned all 4 real INFY/BEML events in the right order; corporate-scores and top-catalysts came back correctly ranked highest-first; negative-news returned 2 genuinely real HDFCBANK/ICICIBANK items about RBI rate-decision uncertainty; biggest-orders/guidance-changes correctly returned empty arrays (honest - no real order-ledger or guidance data exists yet, not a bug); an unauthenticated request correctly received 401. No test data was written anywhere - every endpoint is read-only, so there was nothing to clean up.

Phase 2, fully extended per the user's roadmap (2.1 through 2.10), is now complete. Still explicitly deferred, not started: Phase 2's 2B/2C document-collector expansion (dedicated Conference Call transcript / Investor Presentation collectors), retroactively applying the Observation+Historical Ledger pattern to Phase 1's engines, and the original Phase 1 roadmap's never-built Scoring/Decision Engine (which would blend Technical/Fundamental/Institutional/Sector/Risk, distinct from Module 2.8's narrower corporate-only Corporate Score). Next: Phase 3 - Decision Intelligence, per the original roadmap - not yet scoped.

Phase 3 scoping: re-grounded against everything built since the original Phase 3 discussion (the 2.7-2.10 extension) before proposing a sequence. Confirmed via repo search that the `decision` module was still an empty scaffold, no Scoring/Decision/Rank engine existed anywhere, and Module 2.9's AI Analyst still reasoned over corporate signals only. Reconciled the roadmap's flat module list (AI Analyst, Portfolio Dashboard, Portfolio Risk, Opportunity Comparison, Daily AI Report, Trade Journal, Outcome Tracking) against the standing pre-2.7 decisions (build the missing Scoring/Decision Engine first since "Rank" doesn't exist anywhere; single global portfolio/watchlist, no per-user model) into a 9-module sequence (3.1 Scoring/Decision Engine through 3.9 Outcome Tracking) - a real gap surfaced during this pass: "Portfolio Risk" has no dedicated module in a naive 8-item mapping, since nothing anywhere aggregates risk ACROSS a portfolio (concentration, weighted average Risk Score) - added as its own 3.4, distinct from Portfolio Dashboard (3.3).

Module 3.1 status: DONE (Scoring/Decision Engine - the roadmap's originally-planned Phase 1 module, never built until now that all six real inputs existed). Two real design decisions surfaced and confirmed with the user before building, not assumed: (1) every existing engine's RuleEvaluator (ArithmeticRuleEvaluator) buckets a metric against GTE/LTE/BETWEEN thresholds and sums fixed condition weights on match - correct for turning raw indicators into a score, but lossy when reapplied to inputs that are themselves already six normalized 0-100 composite scores (a 71 and a 99 would contribute identically). Built a new common.rules.WeightedAverageRuleEvaluator instead, still fully DB-driven via common.rule_definitions/rule_conditions like every other rule set, just interpreting condition weight as a coefficient applied directly to the metric's value rather than a threshold-match bonus - required a new RuleOperator.ALWAYS (matches unconditionally, since the rule's presence in a chosen name prefix is what selects it, not a threshold) and a migration widening rule_conditions' operator CHECK constraint (V12). Extracted the threshold-matching switch out of ArithmeticRuleEvaluator into RuleCondition.matches(double) so both evaluators share one implementation of threshold semantics. (2) Horizon-differentiated weighting, confirmed over an equal-weight alternative: Swing Score (short-term) is Technical-led (35%) with Corporate/Institutional/Sector all mattering near-term (20/15/15%) and Fundamental barely (5%); Long-Term Score is Fundamental-led (35%) with Risk and Institutional both mattering a great deal (20/20%) and Sector as near-term noise (5%) - seeded as 12 ALWAYS-operator rules (common/V13), Risk Score needing no sign-flip since it's already "higher = safer" on the same 0-100 scale as every other domain.

Missing-domain handling is a genuine weighted average, not corporate.signal.CorporateSignalEngine's baseline-offset pattern: DecisionScoringEngine renormalizes over whichever of the six domains are actually present (divides by the sum of weights actually used, not a fixed 1.0), so a domain gap is penalized exactly once (via confidence, 10 + 15/domain present) rather than also silently deflating the achievable score ceiling. One real precision bug found and fixed before it reached a test: binary floating-point representation of weights like 0.35/0.15 left sub-cent noise (60.00000000000001) surviving a renormalization division - fixed by rounding the composite to 2dp inside the engine itself, matching decision_scores' actual numeric(5,2) column precision, not by loosening test assertions. swing_rank/long_term_rank are always null out of the engine (a single instrument's own calculation cannot know its rank) - DecisionScoringOrchestrator's driving set is the union of instrument_ids across technical/fundamental/institutional/risk/corporate score tables (sector_score is resolved per-instrument via its sector, not part of the union), and after writing every instrument's score for the day, a second pass runs two DENSE_RANK() window-function UPDATEs to fill both rank columns - ties correctly share a rank rather than being arbitrarily broken. Also added risk.engine.RiskScoreReader (first reader for risk_scores - RiskScoreWriter was write-only until this module needed it, the same pattern every other domain's first reader followed in Module 1.9). Scheduled 21:45 IST, after every upstream scheduler including the latest (News Catalyst, 21:30 IST) - noted but did not attempt to fix a pre-existing sequencing quirk where Corporate Signal (19:45 IST) itself runs before Management Commentary/News Catalyst (21:15/21:30 IST), out of this module's scope.

Verified: 8 new common unit tests (WeightedAverageRuleEvaluatorTest - ALWAYS always matches, contribution scales with both weight and value, non-ALWAYS operators still honor their threshold) + 8 new decision unit tests (DecisionScoringEngineTest - uniform-value/full-coverage/single-domain-renormalization/partial-coverage cases, all 8 rating-band boundaries via single-domain renormalization, confirmed ranks are always null from the engine, confirmed value()==swingScore). Live end-to-end verification against real Postgres used the real domain-score data already on record from Modules 1.5-1.9 and 2.8 (8 real instruments; 4 of them with no corporate_scores row yet, genuinely exercising the missing-domain renormalization path against real data, not synthetic gaps). Hand-verified TCS's real output by hand: technical=100/fundamental=85/institutional=60/sector=100/risk=48.75, corporate absent -> (35+4.25+9+15+4.875)/0.80 = 85.15625, correctly rounded to 85.16, confidence 85.0 (5 of 6 domains). Ranking was genuinely correct across all 8 real instruments, including a real tie: TCS and ESCORTS both landed on long_term_score=72.35 and both correctly received long_term_rank=1 (DENSE_RANK, not ROW_NUMBER). No test-data cleanup needed - every written row is a real computed score for a real tracked instrument, the same "live verification output is production data, not test pollution" precedent Module 2.8 established.

Module 3.2 status: DONE (Watchlist - a single global list, no per-user model, per the standing pre-2.7 decision). Real design question resolved before building: where does watchlist persistence/logic belong? decision/package-info.java already claims ownership ("portfolio, watchlist, comparison, and AI analyst"), so decision.watchlist (WatchlistStore/WatchlistReader/WatchlistService, new decision.api.WatchlistItem) owns the raw CRUD - add(instrumentId) resolves the symbol from reference.instruments and no-ops idempotently via ON CONFLICT DO NOTHING if already listed, returning Optional.empty() when the instrument doesn't exist at all (decision can't depend on api.error.NotFoundException per the module boundary rule, so it returns Optional the same way api.rule.RuleController.activate() already does, letting the api layer translate absence into a 404). Enrichment (showing each watched instrument's current Swing/Long-Term Score and Rank) is an api-layer concern: api.watchlist.WatchlistViewService assembles WatchlistEntryDto by calling decision.watchlist.WatchlistService and the new decision.engine.DecisionScoreReader directly - the same "api module assembles DTOs from domain modules' own readers" pattern api.dashboard.DashboardService established in Module 2.10, not a new abstraction. DecisionScoreReader (findLatest/findHistory/findAllLatest) is decision_scores' first reader - DecisionScoreStore was write-only until this module needed to show a watched instrument's current signal, the same "write-only until X needs it back" pattern every domain's first reader has followed since Module 1.9.

Verified: 6 new api unit tests (WatchlistViewServiceTest, mocking decision.watchlist.WatchlistService and DecisionScoreReader - enrichment when a score exists, null score fields when one doesn't, add/remove delegation, empty-Optional passthrough on an unresolvable instrument). Live end-to-end verification was a full real HTTP round-trip (boot the actual app, log in for a real JWT, curl every endpoint) - the same standard Module 2.10 set as the first HTTP-facing module. Confirmed: unauthenticated GET/DELETE both correctly 401; POST-ing a real instrument (TCS) returns 201 with its actual live Module 3.1 score embedded (85.16 STRONG_BUY swing, rank 1) with zero drift from what Module 3.1's own live verification produced; re-adding the same instrument is genuinely idempotent (identical addedAt, confirming no duplicate row); POSTing a nonexistent instrument id correctly 404s; DELETE correctly 204s once and 404s on a second attempt against the same id; the full list correctly returned both watched instruments with their real enriched scores. Cleanup: removed the two instruments (TCS, INFY) added purely to exercise the endpoints during verification, leaving the watchlist empty for real curation - unlike Module 3.1's decision_scores (genuine computed output from real upstream data), an ad hoc watchlist addition during a live-HTTP smoke test isn't a deliberate curation decision, so it was reverted rather than kept.

Module 3.3 status: DONE (Portfolio Dashboard - single global portfolio, same standing decision as Watchlist). Scope confirmed with the user before building: current holdings only (quantity + weighted-average cost basis after every buy), not a transaction ledger - individual buy/sell events with their own history are explicitly Module 3.8's (Trade Journal) job, not this one's, so the two modules' boundaries don't blur. decision.portfolio (PortfolioStore/PortfolioReader/PortfolioService, new decision.api.PortfolioHolding) owns the raw positions - buy(instrumentId, qty, price) recalculates newAvgPrice = (existingQty*existingAvgPrice + buyQty*buyPrice) / (existingQty+buyQty) or creates a fresh holding at exactly buyPrice; sell(instrumentId, qty) reduces quantity without touching the average cost (a sell realizes against the existing basis, it doesn't change what the remainder "cost"), throws IllegalArgumentException on an oversell (mapped to 400 by the existing GlobalExceptionHandler, no new handler needed), and deletes the row once quantity reaches zero - returning a transient zero-quantity PortfolioHolding as confirmation rather than persisting it (the table's own CHECK constraint forbids a zero/negative quantity or price row). Same Optional-empty-on-unresolvable-instrument shape as Module 3.2's WatchlistService, for the same module-boundary reason.

Enrichment (live mark-to-market value, unrealized P&L, current Swing/Long-Term Score+Rank, current Risk level) is an api-layer concern: api.portfolio.PortfolioViewService assembles PortfolioEntryDto from decision.portfolio.PortfolioService, the new market.api.DailyPriceReader.findLatest (market.daily_prices' first "just the latest price" method - only findHistory/instrumentIdsWithHistory existed before, since nothing before this needed a single mark-to-market value), decision.engine.DecisionScoreReader, and risk.engine.RiskScoreReader directly - the same api-assembles-from-domain-readers pattern as Modules 2.10/3.2. "Live" is explicitly end-of-day, not intraday - there is no real-time price feed anywhere in this project (only the NSE bhavcopy pipeline from Module 1.1), so every entry carries priceAsOfDate alongside currentPrice rather than leaving "current" ambiguous - confirmed with the user during scoping.

Architecture note: market is one of the seven modules ModuleBoundaryArchTest forbids from depending on each other directly, and DailyPriceReader's own doc comment (written back in Module 1.9, before decision existed as a cross-domain aggregator) says "intelligence is the only module allowed to pull this data cross-domain" - but docs/001_System_Architecture.md §4 Rule 4 has always named decision alongside intelligence as allowed to depend on domain modules read-only, and Module 3.1 already established decision doing exactly that for six other domain modules. Read the actual rule before treating a stale comment as ground truth; api.portfolio depending on market.api.DailyPriceReader is fully sanctioned, not a new exception.

Real bug found and fixed via live verification, not by inspection: PortfolioViewService's original P&L-percentage formula divided by (quantity * avgBuyPrice) unconditionally - correct for an open position, but PortfolioService.sell's fully-closed return value carries quantity=0, so a full sell's response computed 0/0 and threw ArithmeticException, surfacing as a 500 despite the underlying delete having already succeeded correctly. Fixed by treating a fully-closed position (quantity <= 0) as having no market value or P&L to report at all (null, not zero) - nothing "unrealized" remains once a position is fully sold - rather than guarding the arithmetic alone; added a regression test for the exact scenario before re-verifying.

Verified: 10 new decision unit tests (PortfolioServiceTest - weighted-average recalculation across multiple buys, oversell/non-positive-quantity/non-positive-price validation, full-close-deletes-the-row, unresolvable-instrument returns empty without touching the store) + 6 new api unit tests (PortfolioViewServiceTest - P&L math both positive and negative, null price/P&L fields with no market data yet, the zero-quantity divide-by-zero regression, Rank/Score/Risk enrichment). Live end-to-end verification was a full real HTTP round-trip against real Postgres and real Module 1.1 price data: bought 10 TCS @2300 then 5 more @2500, confirmed the weighted average landed on exactly 2366.6667 and P&L was computed against the real latest close (2398.00, dated 2026-07-28 - honestly several trading days stale, exactly the "not intraday" caveat given to the user up front) with the real Module 3.1 Swing Score/Rank and Risk level embedded; hit the 500 bug on a full sell, fixed it, restarted the app, and replayed the identical scenario to confirm 200 with null P&L fields and correct removal from the list; oversell correctly 400s with the exact held quantity in the message; buying a nonexistent instrument correctly 404s; unauthenticated buy correctly 401s. Cleanup: the portfolio was already empty at the end of verification (the fully-closed TCS position naturally left no row), so no manual cleanup was needed.

Module 3.4 status: DONE (Portfolio Risk - aggregate risk ACROSS the portfolio, distinct from each holding's own riskLevel Module 3.3 already shows). Real design decision made before building, not asked: unlike decision_scores/corporate_scores, this has no scheduler/table of its own - it's computed fresh on every GET, since portfolio composition changes the instant a buy/sell happens, not on a daily cadence, so a persisted "as of date" snapshot would be meaningless here. Lives in api.portfolio.PortfolioRiskService (same package/resource family as Portfolio Dashboard, new GET /api/v1/portfolio/risk on the existing PortfolioController) rather than decision.portfolio, since this is read-only aggregation over already-enriched data (api.dashboard's own established shape), not new owned state. Reuses PortfolioViewService.list()'s output directly - added a riskScore numeric field to PortfolioEntryDto alongside the existing riskLevel string, so the weighted-average math has the real number to work with, not just its band label.

Computes: market-value-weighted Risk Score (holdings with no price yet are excluded entirely; holdings priced but with no risk score yet are excluded from the weighted average specifically but still count toward total value and concentration - riskScoreCoveragePercent discloses what fraction of value the weighted score actually reflects), single-holding concentration (top holding's % of total value), and sector concentration (grouped via a direct reference.instruments/reference.sectors join - the same lightweight raw-SQL-via-JdbcTemplate pattern every symbol lookup in this codebase already uses, rather than adding Spring/JDBC wiring to the reference module just for this, since reference currently has zero Spring dependencies and no Java readers at all). weightedRiskLevel reuses risk.engine.RiskEngine's exact classify() band boundaries (>=80 VERY_LOW/>=60 LOW/>40 MEDIUM/>20 HIGH/else VERY_HIGH) via risk.api.RiskLevel directly, so a portfolio-level label means the same thing as a per-holding one. Concentration bands (25/40/60% single-holding, 30/50/70% sector) are this module's own disclosed defaults - conventional portfolio-management rules of thumb, not derived from any external source, and easy to change later since they're just numbers in one function, not schema.

Architecture note recorded and left uncorrected on purpose: market.api.DailyPriceReader's own doc comment (Module 1.9) claims "intelligence is the only module allowed to pull this data cross-domain," but docs/001_System_Architecture.md §4 Rule 4 has always named decision alongside intelligence, and Module 3.1/3.3 already established decision/api depending on domain modules directly. Noted again here as a live reminder to keep in mind, not re-fixed a second time.

Real bug found and fixed via live verification, not by inspection: weightedRiskScore was returned as a raw, unrounded double (46.0931278031605 in the live 3-holding test) - correct arithmetic, but inconsistent with every other score in this codebase's 2-decimal display convention (48.75, 38.75, etc.) and visibly different in kind from them. Fixed by rounding to 2dp with the same rationale DecisionScoringEngine (Module 3.1) already used for the identical class of binary-floating-point weighted-average noise; added a test with deliberately non-clean inputs (1000@10.0, 2000@15.0 -> 13.3333... -> 13.33) before re-verifying live.

Verified: 8 new api unit tests (PortfolioRiskServiceTest - empty portfolio produces all-null rather than dividing by zero, unpriced holdings excluded, single-holding 100%/VERY_HIGH, genuine value-weighting proven against what a naive unweighted average would give, risk-score-less holdings excluded from the average but still counted toward concentration, multi-holding sector aggregation, unmapped sector falls back to "Unclassified" rather than being silently dropped, the 2dp rounding fix). Live end-to-end verification against real Postgres and real Module 1.1/3.1/1.9 data: built a real 3-holding portfolio (TCS/RELIANCE/INFY, ₹47,714 total, 2 real sectors) via the same live HTTP flow as Module 3.3, hand-verified every number in the response against the real per-holding data already shown by GET /api/v1/portfolio (concentration 50.26%/HIGH for TCS, sector 73.43%/VERY_HIGH for IT with TCS+INFY correctly summed together, weighted risk score (11057×48.75 + 12677×38.75 + 23980×48.75)/47714 = 46.09/MEDIUM matching by hand to the cent); caught the raw-double bug on this same real run, fixed it, restarted, and replayed the identical real scenario to confirm the rounded value; unauthenticated GET correctly 401s; empty-portfolio case correctly verified both before adding holdings and after selling out of all three. Cleanup: sold all three positions back to zero, confirming both GET /api/v1/portfolio and GET /api/v1/portfolio/risk correctly return empty/all-null again.

Module 3.5 status: DONE (Opportunity Comparison - any N instruments side by side). By far the lightest Phase 3 module so far: decision.api.DecisionScore (Module 3.1) already carries all six domain scores plus Swing/Long-Term Score, Rating, and Rank in one record, so this needed zero new decision-module code - api.comparison.ComparisonService purely re-assembles decision.engine.DecisionScoreReader.findLatest(instrumentId) per requested id into ComparisonEntryDto, the same api-assembles-from-domain-readers pattern every prior read endpoint uses, just with nothing left to compute.

Two read-endpoint edge cases decided without asking, both matching this project's established null-tolerant/honest-about-gaps conventions rather than erroring: an instrument with no DecisionScore yet is still included in the response (all score fields null, symbol resolved via the same reference.instruments fallback every orchestrator's resolveSymbol already uses) rather than silently vanishing from a comparison the caller explicitly asked for; an instrument id that doesn't exist in reference.instruments at all is silently skipped rather than 400ing the whole request over one bad id in a list. Results sort by swingRank ascending with nulls last (Comparator.nullsLast) - best opportunity first, matching every other ranked list in this project.

GET /api/v1/comparison?instrumentIds=id1,id2,... - Spring's default comma-separated List<UUID> binding needed no custom converter.

Verified: 5 new api unit tests (ComparisonServiceTest - full domain-score population for a scored instrument, null-fields-with-real-symbol for an unscored one, silent skip for a nonexistent id, sort order with a null rank mixed in, empty request). Live end-to-end verification against real Postgres and real Module 3.1 data: compared TCS/INFY/BEML plus one garbage id in one request - all three real instruments came back with their exact real scores (TCS 85.16/rank 1, INFY 73.38/rank 4, BEML 51.00/rank 8), correctly sorted, the garbage id silently absent from the response rather than erroring; a single-instrument request and an all-garbage-ids request (empty array, not an error) were also confirmed; unauthenticated GET correctly 401s. Read-only throughout - no data written, nothing to clean up.

Module 3.6 status: DONE (Daily AI Report - a once-daily synthesized digest). Scoped with the user before building (their explicit "scope it first, then build"): unlike Module 3.4's Portfolio Risk, this is scheduled-and-persisted, not computed on every GET - a live view made sense for cheap arithmetic, but every request here would mean a real Claude API call and could produce a different narrative for the same day, so decision.report.DailyReportScheduler runs once at 22:00 IST (after DecisionScoringScheduler and every corporate-domain scheduler for the day) and GET /api/v1/daily-report just reads the persisted result. Lives in decision.report, not intelligence - decision already depends on intelligence (established since the Module 0.3 scaffold), so the reverse would be a circular Gradle dependency; decision already has corporate on its classpath too (from Module 3.1), so it reaches the existing shared AnthropicClient bean (corporate.knowledge.AnthropicClientConfig) directly, needing only its own anthropic-java compile dependency (implementation scope isn't transitive, the same reason intelligence needed its own declaration in Module 2.9).

New decision.report.DailyReportClient is a genuinely separate Claude caller from Module 2.9's AiAnalystClient, not a reuse - that one explains one instrument's Corporate Score history, this one synthesizes a whole day's cross-instrument digest, a materially different prompt shape - but follows the identical "AI explains, it never calculates" discipline: the prompt forbids introducing any number not already present in the supplied facts, and DailyReportEvidenceBuilder computes every number (rank deltas, event/guidance/news counts) deterministically in Java before Claude ever runs. Added DecisionScoreReader.findAllByDate (decision_scores' first "whole cohort on one date" query - every prior reader method was single-instrument-scoped) specifically so the day-over-day rank diff compares one day's full cohort against the prior day's in two queries, not N per-instrument history walks. Watchlist/Portfolio highlights are capped at the top 3 movers by |rank delta| each (calling decision.watchlist.WatchlistService/decision.portfolio.PortfolioService directly, both already public), so the evidence list stays bounded even as either grows - a mover with zero rank change is excluded entirely, not just deprioritized.

Schema (decision.daily_reports, report_date UNIQUE): narrative plus a handful of scalar highlight columns (top gainer/decliner symbol + rank delta, event/guidance/news counts) computed directly from the evidence builder's own numbers, not re-parsed from the narrative's prose later - deliberately not a JSON blob, matching this project's consistent normalized-schema convention (the underlying event/news/guidance detail is already independently queryable via the existing Dashboard endpoints).

Verified: 3 new decision unit tests (DailyReportClientTest - empty-facts short-circuit, calculation-forbidding language, RANK_IMPROVEMENT/RANK_DECLINE and WATCHLIST_MOVER/PORTFOLIO_MOVER prioritization instruction) + 5 new decision unit tests (DailyReportEvidenceBuilderTest - biggest gainer/decliner identification across a cohort, no-prior-day-data graceful degradation, corporate event/guidance/news fact conversion with real counts, watchlist highlight capping at 3 with zero-delta correctly excluded as a genuinely separate concern from the cap itself, portfolio highlights proven independent of watchlist ones). Live end-to-end verification used the user's real ANTHROPIC_API_KEY (pasted directly in chat, passed only as a one-off env var to the single verification command, never persisted) against real Postgres: since only one day of decision_scores existed on record, inserted one synthetic prior-day (2026-08-05) row each for TCS and INFY with swapped ranks (matching Module 2.9's own "insert one synthetic yesterday row" precedent for exercising a day-over-day path), added TCS to the watchlist and INFY to the portfolio directly via SQL to exercise both highlight types, then ran the orchestrator through a temporary bootstrap ManualVerificationTest. The real Claude response was genuinely accurate and fully grounded: correctly cited TCS's real Rank 4->1 improvement and INFY's real Rank 1->4 decline (both exactly 3, matching the deterministic facts), correctly flagged both as "Watchlist Alert" and "Portfolio Alert" respectively, and introduced no number anywhere that wasn't already in the supplied facts; the persisted scalar columns matched the narrative exactly (top_gainer_symbol=TCS/improvement=3, top_decliner_symbol=INFY/decline=3, all counts 0 since no real events/guidance/news existed for the day). Confirmed the same result was correctly served back over real HTTP via both GET /api/v1/daily-report and GET /api/v1/daily-report/2026-08-06, unauthenticated GET correctly 401s, a nonexistent date correctly 404s. Cleanup: deleted the synthetic 2026-08-05 decision_scores rows, the TCS watchlist entry, the INFY portfolio holding, and the daily_reports row itself (unlike Module 3.1's decision_scores, this report's content was contingent on the synthetic setup just removed, so keeping it would have left a "daily report" describing state that no longer reflects reality - a genuine test artifact, not organic production output).

Module 3.7 status: DONE (Extend AI Analyst to explain Rank changes - the roadmap's own worked example, "Why moved from Rank 18 to Rank 5?"). Real architectural finding before any code changed: extending explainScoreChange to also explain Rank required decision.engine.DecisionScoreReader, which the existing AI Analyst (intelligence.analyst, Module 2.9) cannot depend on - decision already depends on intelligence (since the Module 0.3 scaffold), so the reverse would be a circular Gradle dependency, the identical constraint Module 3.6 hit building the Daily Report. Rather than duplicate a second analyst package (as 3.6 did for a materially different prompt shape), relocated the entire existing AI Analyst - AnalystEvidenceBuilder/AiAnalystClient/EvidenceFact/AiAnalystService, and both test files - from intelligence.analyst to decision.analyst via git mv, preserving history. This is a correction, not just a workaround: decision/package-info.java has claimed "AI analyst" as one of its four owned capabilities since Module 0.3's original scaffold, and decision already depends directly on every domain module (corporate, sector, technical, financial, ownership, risk) the evidence builder needs, since Module 3.1 - the original intelligence-bridging rationale from 2.9 simply no longer applied. Removed intelligence's now-unused anthropic-java compile dependency as part of the same cleanup (nothing else in intelligence touches the Anthropic SDK directly). Full build (including ArchUnit) confirmed green immediately after the move, before any new capability was added - isolating "did the move break anything" from "does the new capability work."

New capability: AnalystEvidenceBuilder.buildRankEvidence(instrumentId) computes rank-specific facts (Swing Rank delta via the existing DecisionScoreReader.findHistory, no new reader needed; per-domain score deltas across all six domains at a >=5-point threshold) and then reuses five of buildScoreEvidence's existing six fact-gathering methods unchanged (order book, management, sector standing, news catalyst, corporate events, graph relationships) - those facts still explain WHY the underlying scores moved, they simply weren't connected to a Rank before Module 3.1 gave the platform one. AiAnalystService gained explainRankChange(instrumentId) as a sibling to the renamed explainScoreChange (was buildEvidence/implicit) - both share the exact same AiAnalystClient and its Claude-calling prompt unchanged, since the prompt was already generic ("why has this instrument's outlook changed"), never hardcoded to Corporate Score specifically - no new Claude client needed, unlike Module 3.6 where the prompt shape genuinely differed.

First HTTP exposure of the AI Analyst: Module 2.9 built the service but never a controller. api.analyst.AnalystController adds GET /api/v1/analyst/{instrumentId}/score-explanation and /rank-explanation, both read-only (a valid JWT only) - each call is a real, uncached Claude API call, not persisted like Module 3.6's scheduled Daily Report, so this endpoint is naturally lower-traffic than every other GET in the project by design, not oversight.

Verified: all 17 pre-existing analyst unit tests (14 AnalystEvidenceBuilderTest + 3 AiAnalystClientTest) passed unchanged after the relocation - proof the move altered no behavior - plus 9 new AnalystEvidenceBuilderTest cases for the rank capability (empty/single-day history graceful degradation mirroring the existing score-history handling, correct improve/decline direction and magnitude, an unchanged rank correctly producing no fact at all, per-domain delta threshold and null-score-skip behavior, confirmation that rank evidence still carries the same corporate-side context score evidence does). Live end-to-end verification used the user's real ANTHROPIC_API_KEY again (one-off env var, not persisted) against real Postgres: inserted one synthetic prior-day TCS row (2026-08-05, Swing Rank 18, Technical Score 70) against the real Module 3.1 today's row (Rank 1, Technical Score 100) - deliberately mirroring the roadmap's own "Rank 18" framing. The real Claude response was fully grounded and genuinely accurate both calling the service directly and over real HTTP: correctly cited the exact 18->1 rank move (17-position improvement), the exact 70.0->100.0 Technical Score jump, and the real Information Technology sector standing (Sector Score 100.0, VERY_STRONG momentum) reused from the existing corporate-context facts - no fabricated number anywhere in either response. Unauthenticated GET correctly 401s on both endpoints. Cleanup: deleted the synthetic 2026-08-05 row; no watchlist/portfolio/report state was touched by this module, so nothing else needed reverting.

Module 3.8 status: DONE (Trade Journal - auto-recorded from Portfolio buy/sell, option 1 from the two designs discussed with the user, chosen specifically to avoid double-entering the same trade). decision.journal (TradeJournalStore/Reader/Service, new decision.api.TradeJournalEntry/TradeAction) is purely a byproduct: recordBuy/recordSell are called only by decision.portfolio.PortfolioService right after a trade actually executes, never independently - there is deliberately no POST endpoint on the journal itself, only GET /api/v1/trade-journal and /{instrumentId}. Immutable, one row per trade, matching corporate module's own Layer-1 "never revised" pattern.

Real gap surfaced and fixed before building the journal itself, confirmed with the user during scoping: Module 3.3's sell endpoint never captured an execution price - unrealized P&L only needed the remaining position's average cost basis, so it didn't need one at the time. But a trade journal's core purpose is recording realized P&L, which is impossible without knowing what was actually sold at. Added a required price field to PortfolioService.sell/SellRequest - a genuine breaking change to the already-shipped Module 3.3 endpoint, made deliberately rather than worked around, since the old behavior was a real oversight now blocking a real downstream need. The average cost basis itself still stays untouched by a sell, exactly as 3.3 originally designed - the sell price is used only to compute realizedPnl = quantitySold * (sellPrice - costBasisAtSale), with costBasisAtSale captured from the holding BEFORE PortfolioService mutates or deletes it, so the number is self-explanatory and auditable later without needing portfolio_holdings' own history (which retains none - current-position-only was 3.3's own deliberate scope boundary). Both buy and sell also gained an optional rationale field, passed straight through to the auto-created journal entry - the "why" a transaction ledger alone can't capture.

Deliberately out of scope, left for Module 3.9: aggregate outcome stats (win rate, average realized return, etc.) - keeping the 3.8/3.9 boundary as clean as the 3.3/3.8 one, this module only records and lists individual trades.

Verified: 14 updated decision.portfolio.PortfolioServiceTest cases (weighted-average math unchanged; new coverage for journal-entry recording on both buy and sell with the exact rationale/quantity/price passed through; realized P&L math both at a profit and a loss; zero journal interaction on every failure path - non-positive quantity/price, nonexistent instrument, oversell, no existing holding) + 4 new decision.journal.TradeJournalServiceTest cases (store/reader delegation) + 6 updated + 2 new api.portfolio/api.journal view-service tests (BUY entries carry no cost basis/realized P&L, SELL entries carry both, computed tradeValue). Live end-to-end verification against real Postgres and real HTTP: bought 10 TCS @2300 (with rationale) then 5 more @2500 (without), confirmed both BUY journal entries recorded with the exact quantity/price/rationale; sold 5 @2600 (profit) and the remaining 10 @2200 (loss), hand-verified both realized P&L figures exactly (1166.6665 and -1666.6670) against the real weighted-average cost basis (2366.6667) captured before each sell; confirmed oversell and a nonexistent-instrument buy both correctly 404 without creating any journal entry (count stayed at exactly 4 throughout); unauthenticated GET correctly 401s. Cleanup: deleted all 4 test journal entries and confirmed both the journal and portfolio were empty again afterward.

Module 3.9 status: DONE (Outcome Tracking - the last module in the reconciled Phase 3 sequence). Scoped and confirmed before building: aggregate outcomes across every closed (SELL) trade the Trade Journal (Module 3.8) already records - total realized P&L, win/loss/break-even counts and win rate, average win vs. average loss kept deliberately separate (not one blended average, which hides whether a positive total came from many small wins or one large one), best/worst trade, and a per-instrument breakdown. Same compute-on-demand shape as Module 3.4's Portfolio Risk (no new table or scheduler - cheap arithmetic over already-persisted data), living alongside the existing api.journal package with a new GET /api/v1/trade-journal/outcomes on the existing TradeJournalController, reusing TradeJournalViewService's already-enriched DTOs rather than re-deriving the raw-to-DTO mapping a second time.

Deliberately not built, flagged explicitly rather than silently skipped: correlating realized outcomes back to the Swing Rating that was active at trade time (the "did our own signal actually predict good outcomes" question) - genuinely the more interesting analytical question, but Trade Journal doesn't capture the rating at trade time, and reconstructing it after the fact gets tangled with partial buys against a weighted-average cost basis (which buy's rating "counts" when a sell realizes gains against a blended cost basis from several purchases at different times/ratings?). Left as a real, named extension rather than building it on an ambiguous definition just to appear complete.

Verified: 7 new OutcomeTrackingServiceTest cases (empty journal produces an all-null summary rather than a fabricated 0% win rate; open positions with only BUYs produce zero closed trades; win rate/total P&L/separate average win and loss computed correctly across a mixed win-loss set; break-even trades counted separately but still counted toward the win-rate denominator; all-wins correctly leaves averageLoss null; best/worst trade correctly identified by realized P&L; per-instrument grouping sorted by realized P&L descending). Live end-to-end verification against real Postgres and real HTTP: confirmed the summary was genuinely all-null before any trade existed; built a real two-instrument trade history (TCS bought 10@2300 and sold 10@2500 for a clean win; INFY bought 10@1100, sold 5@1050 for a loss and the remaining 5@1100 for an exact break-even) and hand-verified every figure in the response exactly - total realized P&L 1750 (2000-250+0), win rate 33.33% (1 of 3 closed trades), average win 2000, average loss -250, best trade correctly the TCS sell, worst trade correctly the INFY loss sell, and the per-instrument breakdown correctly ranked TCS (+2000) ahead of INFY (-250) with INFY's own 0% win rate (2 closed trades, 0 wins) computed correctly. Unauthenticated GET correctly 401s. Cleanup: deleted all 5 test journal entries; portfolio was already empty since every position was fully sold down during verification.

Phase 3 (Decision Intelligence) is now complete - all 9 modules (3.1 Scoring/Decision Engine through 3.9 Outcome Tracking) built, live-verified against real Postgres data and, where AI narration was involved (3.6, 3.7), the real Claude API. AlphaGraph now has: a unified Rank blending all six domain scores; a global Watchlist and Portfolio Dashboard with live P&L; Portfolio Risk (concentration + weighted Risk Score); side-by-side Opportunity Comparison; a scheduled Daily AI Report; an AI Analyst that explains both Corporate Score and Rank changes; an auto-recorded Trade Journal with realized P&L; and Outcome Tracking closing the loop. Still explicitly deferred, not started: Phase 2's 2B/2C document-collector expansion, retroactively applying the Observation+Historical Ledger pattern to Phase 1's engines, and Phase 4 (Learning Intelligence) - not yet scoped.

Frontend (web/, Vite + React 19 + TypeScript, "modern fintech" light theme) built across a separate session after Phase 3: all 7 IA pages (Rankings as landing, Dashboard, Watchlist, Portfolio, Compare, Daily Report, Trade Journal) plus an Instrument Detail drill-down with on-demand AI Analyst explanations, each verified live against the real backend (not just typechecked) - including a genuine buy/sell round-trip on Portfolio, a real Claude-narrated Daily Report generation, and real AI Analyst explanations, all using the user's own ANTHROPIC_API_KEY as a one-off env var, never persisted, backend always restarted keyless afterward. Followed by a polish pass (retry-capable ErrorState replacing dead-end error text, a 404 catch-all route, a mobile-responsive sidebar) and loading skeletons (Skeleton/TableSkeleton/WidgetCardSkeleton/StatTilesSkeleton) replacing plain "Loading…" text everywhere, verified with a real temporary delay injected into apiFetch (reverted before commit) rather than trusting code review alone.

AI Analyst caching retrofit (Module 3.7 follow-up): explainScoreChange/explainRankChange were a real, uncached Claude call on every single request - a deliberate choice at the time ("naturally lower-traffic than every other GET endpoint by design"), but the user correctly identified this breaks down once many users can ask the same question about the same instrument on the same day - thousands of users clicking "Explain Corporate Score" would mean thousands of billed Claude calls re-narrating identical underlying facts. New decision.analyst_explanations table (V6 migration) caches one explanation per (instrument_id, explanation_type, business_date) - the underlying decision/corporate scores only change on a scheduled recompute at most once daily, so a same-day cache hit is never stale. AiAnalystService.explainCached() checks the cache first; a hit skips symbol resolution, evidence building, and the Claude call entirely. Mirrors decision.daily_reports' own once-per-day convention (Module 3.6). Verified: 4 new unit tests (cache hit skips evidence builder/Claude/store for both explanation types; cache miss builds the correct evidence - score vs. rank, never crossed - and writes the result) + live E2E against real Postgres and the real Claude API: call 1 for INFY's rank-explanation took 6.49s (genuine Claude call), call 2 took 0.10s and returned byte-for-byte identical output; a separate score-explanation call for the same instrument correctly missed independently (8.2s, proving the cache key includes explanation type); finally restarted the backend with no API key configured at all and confirmed the already-cached explanation still served correctly in 0.11s - proving a cache hit needs no credential, the actual payoff at scale.

News relevance pre-filter + admin review queue (Module 2.6 retrofit): every RSS-collected news article previously went straight to two unconditional Claude calls (Stage 1 classify + NewsExtractor) regardless of relevance - the general "Markets" RSS feeds have no per-company scoping, so most collected articles were never about any tracked instrument at all, a real disclosed waste the user identified by asking "does the extractor filter out irrelevant news before calling Claude?" The user's follow-up design ("store every news received from the feed and show it to admin... if admin keeps it then call claude api for that", later narrowed to "only show the filtered out news to admin") produced a human-in-the-loop moderation queue, not a silent auto-discard filter. New corporate.newsfeed.NewsRelevanceFilter does a cheap, deterministic whole-word/case-insensitive match against every tracked instrument's symbol and (legal-suffix-stripped) company name; NewsFeedLoader now branches on the verdict before insert - a match still flows straight to automatic extraction (status PROCESSED, unchanged path), a non-match lands as PENDING_REVIEW (two new corporate.documents status values, V12 migration) instead of being silently discarded or auto-extracted. An admin reviews exactly the filtered-out articles whenever they get to it, not necessarily in real time, via the new ADMIN-only api.admin.NewsReviewController and a new /admin/news-review web page: Keep flips the row to PROCESSED and calls a new KnowledgeExtractionOrchestrator.extractDocument(UUID) immediately (bypasses waiting for the next scheduled run); Discard marks it DISCARDED, terminal.

Verified with 14 new unit tests (word-boundary matching so short symbols like ACE don't match inside "faced"/"space"; status branching; duplicate-title dedup still short-circuits before the filter runs; keep/discard are atomic via a conditional UPDATE, safe against a double-click or already-decided article) plus live E2E against real Postgres, the real live RSS feeds, and the real Claude API: manually triggered the actual corporate-news-rss pipeline and got a genuine 101 filtered-out / 11 auto-processed split from live feed data; spot-checked both sides (filtered-out were genuinely unrelated PIB/government releases, auto-processed genuinely mentioned TCS/HDFC Bank/ICICI Bank); tested Discard (204, DISCARDED) and Keep (204, 24.4s real Claude call, correctly reached KNOWLEDGE_EXTRACTED with real topics written) via curl, then re-verified the identical flow by clicking the actual rendered buttons in the browser. One genuine, disclosed finding from live data, not patched: the RELIANCE symbol false-positived on the plain English word "reliance" ("...reducing reliance on forward guidance...") since simple whole-word matching can't distinguish a stock symbol from an identically-spelled dictionary word (unlike TCS/INFY/HDFCBANK/etc., which aren't real words) - accepted as a minor cost consistent with the filter's deliberate bias toward over-inclusion over silently missing real news, rather than fragile per-symbol special-casing.

Local dev note: the scheduler's admin recovery endpoint (POST /pipeline-definitions/{id}/run) needs a pipeline's DB id, which only exists after its first run - a brand-new pipeline can't be triggered that way until either the 6PM cron fires once, or you call DailyPipelineScheduler.runScheduledPipelines() directly (e.g. via a test) to force the first registration.

Module 1.1 follow-up - real HttpBhavdataCollector: DONE. Confirmed https://archives.nseindia.com/products/content/sec_bhavdata_full_DDMMYYYY.csv is live (contradicts a stale "discontinued 2024" claim found during research - likely conflated with a different, retired plain-bhavcopy report). HttpBhavdataCollector active only in docker/prod profiles (@Profile), bundled sample stays default for local/CI. URL template configurable via alphagraph.market.nse-bhavdata-url-template. Fixed two real bugs found via live verification: (1) PipelineOrchestrator always said "Completed" even on a FAILED/zero-rows run - now short-circuits on rowsRead==0 with a clear "FAILED - <reason>" message, skipping quality scoring entirely; (2) HttpBhavdataCollector's two constructors confused Spring DI (tried a nonexistent no-arg constructor) until @Autowired was added - every other bean in the codebase happens to have exactly one constructor, so this class of bug hadn't surfaced before. Verified against the real live NSE server: 2,408 real rows fetched, 8 loaded, 2,400 correctly quarantined, no crash.

Local dev environment notes (for resuming without rediscovery):
- Repo: https://github.com/nitra-1/AlphaGrpah (main branch)
- JDK 17 at D:\java runs Gradle itself; Gradle 8.11 at D:\gradle; GRADLE_USER_HOME set to D:\gradle-home (space-free path)
- The project's Java 21 toolchain is auto-provisioned by Gradle (foojay-resolver-convention in settings.gradle.kts) into D:\gradle-home\jdks - no manual JDK 21 install needed
- Windows quirk: this machine's username has a space in it, which breaks the JDK's internal loopback socket (java.nio Selector) unless JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/tmp is set. Already set as a permanent user env var, so new shells pick it up automatically
- A standalone local PostgreSQL 17 test instance runs on port 5434 (data dir D:\pgdata, role alphagraph_app / alphagraph_local_dev, db "alphagraph") - separate from docker-compose.yml's port-5432 setup, used only for manual verification. This machine also has a *shared* postgresql-x64-17 Windows service (data dir D:\Program Files\PostgreSQL\17\data, port 5433) that other local projects depend on - never reconfigure or repoint that service for this project; D:\pgdata is started independently instead: `pg_ctl -D D:\pgdata -o "-p 5434" -l D:\pgdata\startup.log start` (no admin rights needed - D:\pgdata is owned by the regular user account, unlike the shared service's data dir)
- Build/test/verify command: cd E:\Alpha && JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/tmp ./gradlew build

Add Financial/Shareholding Data admin form: the second and final piece the security master work was groundwork for - closes the last gap from the original "Add Instrument" plan. A newly-added instrument (via the prior feature) gets real price history and Technical/Sector/Risk scores automatically, but Fundamental and Institutional scores stay null forever without this - there was no way to enter financial results or shareholding pattern except hand-editing the bundled CSVs and restarting the backend, exactly the technical burden the whole non-technical-hire feature exists to remove.

Deliberately reuses infrastructure instead of duplicating it: new POST /admin/financial-results and /admin/shareholding-pattern endpoints call the exact same FinancialInstrumentLookup/FinancialResultsLoader and OwnershipInstrumentLookup/ShareholdingLoader classes the bundled-CSV pipeline already uses (both upsert on their natural key, so re-submitting a form to correct a figure is safe). New reference.instrument.InstrumentReader.listAll() + GET /admin/instruments powers the form's instrument picker, independent of whether an instrument has any scores yet (unlike Rankings, which requires a computed score to list something). Every field mirrors its underlying schema's own nullability - only symbol/period/sales/pat (financial) and symbol/period/promoter/fii/dii (shareholding) are required, matching docs/006_Universe_Expansion_Runbook.md §4's "leave blank rather than guess" rule field-for-field.

Two real bugs caught live, neither visible from curl testing - both required an actual browser: both new POST endpoints originally returned 201 CREATED with an empty body (matching a lazy first draft, not the existing InstrumentController convention of returning the created resource). web/src/lib/api.ts's apiFetch only special-cased empty bodies for status 204, so response.json() threw "Unexpected end of JSON input" on the empty 201 - the database write genuinely succeeded both times, but the frontend showed no success message and never reset the form, silently presenting a real success as an apparent failure. Fixed two ways: (1) both controllers now return the created FinancialResult/ShareholdingPattern instead of an empty body, matching the existing convention; (2) apiFetch itself made more robust - reads response.text() and checks for actual emptiness rather than trusting status-code special-casing, so the same class of bug can't recur for some future endpoint that also forgets to attach a body. Caught and fixed only because the verification discipline this session insists on (real browser, not just curl) was followed even after the backend-only checks all passed.

Verified live end-to-end: researched real Q4 FY25 figures for WIPRO (Sales ₹22,504.2 Cr, PAT ₹3,569.6 Cr, EPS ₹3.41 - Business Today/Business Standard agree) and HCLTech (Sales ₹30,246 Cr, PAT ₹4,307 Cr, quarterly EPS not found in any source so left blank rather than substitute the annual figure), entered both through the real rendered form (instrument picker, date/number fields, submit), confirmed byte-for-byte correct in financial.financial_results afterward. WIPRO's shareholding pattern (Promoter 72.62%, FII 10.79%, DII 7.86% - one source disagreement with a second site's 3.55% DII figure, not resolved arbitrarily, Angel One's paired FII+DII figures used since they're specifically reported together) also verified correct in ownership.shareholding_pattern. Full ./gradlew build green.

Add Instrument admin form: the actual non-technical-hire-facing feature the security master ingestion was groundwork for. Three new pieces wired together: (1) reference.instrument (InstrumentWriter, SectorService) - reference.instruments' first-ever *runtime* write path, every prior instrument (original 8 + batch 1's 12) only ever came from a Flyway migration. SectorService.findOrCreateByName() looks up by name before inserting, same defensive pattern V4/V5 already established for reference.sectors having no unique constraint on name. (2) market.pricing.HistoricalBackfillService - productionizes the manual NSE-fetch process from docs/006 into a real @Async service: 120 calendar days ending yesterday (a dynamic, always-current window, deliberately different from batch 1's fixed historical window, which only existed to align the original 20 on one shared range), reusing the exact full-row-split column approach BhavdataParser/SecurityMasterParser already use rather than a capture-group slice - an early draft used a regex capture group starting after "SYMBOL, EQ, " and hand-indexed the remaining fields assuming the original column positions, which silently shifts every index by 2; caught before ever running against live data by rewriting to the same proven whole-row-split-then-index approach used elsewhere, not by debugging bad output after the fact. (3) api.admin.InstrumentAdditionService orchestrates all three modules (reference.instrument/reference.securitymaster/market.pricing never depend on each other directly, so this cross-module flow can only live in api) - company name and ISIN are never taken from the request, only `symbol` is client-supplied and re-verified server-side against reference.security_master, closing the loop the whole feature exists for: a non-technical admin can no longer type or need to independently verify an ISIN.

One real Spring DI bug caught at boot, not silently: HistoricalBackfillService has two constructors (one for Spring, one package-private for tests, same pattern as HttpBhavdataCollector) but the public one was missing @Autowired - Spring couldn't disambiguate and failed with a confusing "No default constructor found" rather than a clear ambiguity error. Fixed by adding the same @Autowired annotation HttpBhavdataCollector's own history already called out as necessary for this exact scenario.

Verified live end-to-end against real Postgres and the real NSE endpoints: created WIPRO (a real, previously-untracked NSE stock) via GET /admin/sectors, GET /admin/security-master/search?query=WIPRO, and POST /admin/instruments - landed correctly in reference.instruments with the right ISIN and sector_id. Duplicate re-add correctly 400s ("WIPRO is already tracked"); a fabricated symbol correctly 400s ("isn't in NSE's listed-equity master"). Background backfill completed independently ~1 minute later: 82 real distinct trading days (2026-04-10 through 2026-08-07), 86 URLs fetched, 0 failed - real current OHLCV data, not placeholder rows. Full ./gradlew build green including ArchUnit. Disclosed gap, not silently skipped: the Vite dev server couldn't be started this session (node/npm unreachable through their nvm symlink in this environment - a sandbox issue, confirmed the target directory genuinely doesn't exist in this session, not a retry-and-it-works problem) - every API call the React form makes was verified live via curl instead, but the actual rendered UI wasn't click-tested this time, unlike every earlier frontend page this session.

Security master ingestion (reference.securitymaster): the user wants to hire a non-technical person to add new tracked symbols going forward, following docs/006_Universe_Expansion_Runbook.md - but that runbook still required typing a symbol/ISIN by hand and independently verifying it, exactly the kind of judgment call a non-technical hire shouldn't have to make. The user's own proposed fix was better than what was planned: pre-load NSE's entire real listed-equity universe so a symbol can be picked from a dropdown instead of typed. Closes a gap Module 1.1 explicitly disclosed as unaddressed ("Security Master remains a one-time seed... worth revisiting if new listings/symbol changes need to flow in automatically"). New reference.security_master table (V6 migration) - deliberately separate from reference.instruments; a row existing here never implies the instrument is tracked/scored, only that NSE lists it. Ingests the real, free, live https://nsearchives.nseindia.com/content/equities/EQUITY_L.csv (confirmed live: all 3 spot-checked ISINs - SBIN/M&M/ITC - matched exactly what was independently WebSearch-verified during batch 1), filtered to SERIES=EQ. Same Collector/Parser/Normalizer/Loader shape as every other pipeline, registered with the existing scheduler.PipelineRegistry so it runs in the same daily 18:00 IST batch as everything else - no new scheduling mechanism built just to run this one less often, since the marginal cost of one more low-risk fetch in the existing batch is trivial. Unlike market/corporate's date-parameterized NSE endpoints, this one needed no cookie-bootstrap anti-bot workaround and isn't date-parameterized, so - same reasoning as corporate.newsfeed.RssFeedCollector - there's no bundled-sample/live-HTTP profile split, it's live in every profile including local dev.

Two real bugs caught and fixed before verification, not after: (1) a straight column-index transcription error in the parser (FACE_VALUE and ISIN NUMBER swapped - fields[6] vs fields[7]) caught by re-deriving the index mapping from a real fetched row rather than trusting the first pass; (2) NSE's listing dates are upper-case ("23-AUG-1995") unlike bhavdata's title-case dates ("24-Jul-2026") - a plain "dd-MMM-yyyy" formatter would have thrown on every real row; fixed with DateTimeFormatterBuilder.parseCaseInsensitive() before it ever ran against live data. New api.admin.SecurityMasterSearchController (GET /api/v1/admin/security-master/search?query=..., ADMIN-only) powers the future "Add Instrument" form's autocomplete - search-only, writes nothing to reference.instruments.

Verified live end-to-end against real Postgres: a brand-new ScheduledPipeline has no persisted pipeline_definitions row until its first run (documented local-dev gotcha), so force-ran the full registry once via a throwaway test calling DailyPipelineScheduler.runScheduledPipelines() directly (deleted after verification, not a committed test) - loaded 2,075 real EQ-series securities, all 4 spot-checked symbols (SBIN/M&M/ITC/RELIANCE) matched exactly, pipeline correctly self-registered in pipeline_definitions afterward. Search endpoint verified with real queries: symbol-prefix ("SBI" correctly surfaced SBIN/SBICARD/SBILIFE/SBIFUNDS), company-name substring ("Mahindra" correctly surfaced the whole Mahindra Group), no-match correctly empty, unauthenticated request correctly 401s. Full ./gradlew build green including ArchUnit - reference module gained real HTTP/JDBC code for the first time (previously migration-only) with no module-boundary violation, since market already established the precedent of depending on reference.

Universe expansion, batch 1: tracked instruments grew from 8 to 20. The user wants to use AlphaGraph personally first (before any commercialization) to judge whether it's a credible investment aid, and correctly identified that 8 hand-picked stocks can never surface a genuinely new opportunity - every domain engine only ever scores what's already in reference.instruments. Chose 12 real Nifty large-caps deliberately filling sectors the original 8 didn't touch at all (FMCG, Pharma, Auto, Metals, Cement, Telecom, Power, Consumer Durables were zero-coverage; the "Construction & Engineering" sector row from Module 1.8's dummy tree got its first real constituent, LT) rather than jumping straight to the full Nifty 50/100 - fundamental/shareholding data has no free live bulk source (a pre-existing, disclosed gap), so each addition costs real per-company research, and a small deliberate batch keeps that tractable and checkable. TATAMOTORS was deliberately excluded: a real Oct-Nov 2025 demerger split it into TMPV (passenger vehicles, old ISIN) and a separately-renamed TMCV (commercial vehicles) - Mahindra & Mahindra fills the Automobile slot instead, no corporate-action discontinuity to backfill through. All 12 ISINs verified live via WebSearch, not recalled from training data (V5 migration, reference module) - same discipline as Module 1.1's BEML ISIN fix.

Price history: confirmed HttpBhavdataCollector/HttpAnnouncementsCollector only run under docker/prod profile - locally both read a bundled sample fixed to the original 8, so the 12 new instruments would show zero price/announcement history until either a real backfill or a docker/prod run. Backfilled 75 real trading days (2026-04-09 through 2026-07-28, matching the original 8's exact window) fetched live from NSE's sec_bhavdata_full archive - one HTTP request per weekday, 79 URLs all returned 200 but only 75 distinct dates ever landed, because NSE doesn't 404 a market holiday, it silently re-serves the prior real trading day's file under that date's URL; caught by comparing the fetched-row count against the distinct-date count before writing the migration (market V3), not discovered after the fact. A real bug also caught pre-write: an early unanchored substring grep matched "XITC, EQ," as a false hit for "ITC, EQ," - fixed by anchoring every symbol match to the start of the line. The news RSS pipeline needed no backfill - confirmed live (triggered corporate-news-rss manually) that NewsRelevanceFilter.loadAliases() queries reference.instruments fresh on every call, so all 12 new symbols were already covered with zero code change.

Financial results + shareholding pattern: both are bundled-CSV-only with no free live bulk source (the same pre-existing gap Module 1.6/1.7 disclosed), so 12 companies' real Q4 FY25 quarterly figures and latest shareholding patterns were researched via 3 parallel WebSearch agents rather than fabricated, each explicitly instructed to report NOT FOUND rather than estimate. Real, disclosed judgment calls made reconciling the results: (1) PAT convention - screener.in's "Net Profit" is pre-minority-interest for several companies (M&M, TATASTEEL, ULTRACEMCO, NTPC, ASIANPAINT, LT) while news outlets report PAT attributable to owners (the EPS-consistent figure) - standardized on owners' PAT throughout, except BHARTIARTL where the two figures reflect genuinely different things (one-off Indus Towers stake gain) and neither is clearly "the" number, so the more commonly-cited screener.in figure was kept with the caveat disclosed here rather than silently picked. (2) ITC's PAT (₹19,727 Cr) is real but inflated by a one-off ₹15,179 Cr hotel-demerger gain - stored as-reported (not adjusted, since inventing an "adjusted" figure would be editorializing beyond what was actually verified), but net_margin_percentage was deliberately left null rather than store a >100% figure that would misrepresent core business performance to the Fundamental Engine. (3) ROE/ROCE/balance-sheet fields were left null wherever two sources gave a genuinely conflicting figure (e.g. HUL's ROE came back as both 24% and 31% from the same tool across two fetches; ITC's entire balance sheet was caught being mixed up with annual-period figures) rather than arbitrarily picking one - same discipline as Module 1.6's TCS EPS null. (4) ITC and LT genuinely have 0% promoter holding (no promoter / professionally managed) - stored as real 0.00, not missing data. (5) Shareholding periods vary per company (Mar-2025 where reconcilable with the financial period, Jun-2026 - the only period reliably found - otherwise) since ownership.shareholding_pattern has its own independent period_end column, same pattern as Module 1.7's RELIANCE-only trend data.

Full `./gradlew test` passes (66 tasks, no regressions from the wider universe). Not yet verified end-to-end: Technical/Fundamental/Institutional/Sector/Risk/Corporate/Decision scores for the 12 new instruments haven't computed yet - they run on a scheduled cron cascade (18:00-21:45 IST) that hadn't fired since this data was added (verified at 12:16 IST, same day), and no manual recompute endpoint exists yet. Data is confirmed correctly loaded and readable; the scoring layer itself is disclosed as pending, not claimed as verified.

Multi-tenancy: Portfolio, Watchlist, and Trade Journal move from single-global-state (explicitly disclosed as a standing decision since Modules 3.2/3.3/3.8 - "no per-user model, no investor/renter-style external user model yet") to genuinely per-account. Driven by the user wanting to use AlphaGraph personally first and, eventually, let other real people log in with their own portfolios - which the single-global-state design could never support no matter how much UI polish went on top of it. User explicitly chose admin-provisioned accounts only, no public self-signup, via AskUserQuestion.

api.platform_users.role gained a real USER value alongside ADMIN/SYSTEM (api V3 migration, widened CHECK constraint). JWT now carries userId (a UUID claim) alongside email/role - JwtService.AuthenticatedPrincipal is the full principal object set on the Spring Security Authentication (JwtAuthenticationFilter), not just the email string, so every controller can pull userId out via `@AuthenticationPrincipal JwtService.AuthenticatedPrincipal`. New ADMIN-only `POST/GET /api/v1/admin/users` (UserAdminController) is the only way a new account gets created - hashes the password server-side, 400s cleanly on a duplicate email via DuplicateKeyException rather than a raw 500. New "Add User" admin page (web/src/routes/AddUserPage.tsx), same no-SQL/no-CLI admin-form pattern as Add Instrument/Add Financial Data.

decision.portfolio_holdings/watchlist_items/trade_journal_entries all gained a `user_id uuid NOT NULL` column (decision V7 migration) - value-only reference to api.platform_users.id, no cross-schema FK, the same established convention as instrument_id referencing reference.instruments (docs/003_Database_Architecture.md §2). Existing rows were backfilled to the seeded admin account rather than silently orphaned or dropped, so admin's pre-existing portfolio/watchlist/journal history becomes "admin's own," not lost - verified directly via psql after migration (1 portfolio row, 3 journal rows, all correctly attributed to admin@alphagraph.local). Unique constraints changed from `(instrument_id)` to `(user_id, instrument_id)` - "one holding of a stock, platform-wide" becomes "one holding of a stock, per user." Every Reader/Store/Service method in decision.portfolio, decision.watchlist, and decision.journal now takes `UUID userId` as an explicit first parameter and filters/scopes by it - no ambient "current user" state anywhere in the decision module, consistent with the module-boundary rule that decision can never import from api (docs/001_System_Architecture.md §4), so userId has to be resolved in the api layer from the JWT and passed down as a plain value, never as an api-layer type.

One real gap deliberately not solved here, disclosed rather than hidden: decision.report (Daily AI Report, Module 3.6) is still one platform-wide report per date - decision.daily_reports has no user_id, and the report is generated by a single scheduled cron run with no request-scoped user to scope to. Its Watchlist/Portfolio highlight facts are pinned to the seeded admin account (the same account decision V7 backfilled everything to) rather than genuinely covering every user's watchlist/portfolio. Making the Daily Report itself per-user is real future work, not attempted in this pass.

One real bug found and fixed via live two-user testing, not caught by unit tests or the build: api.portfolio.PortfolioController and api.watchlist.WatchlistController still carried `@PreAuthorize("hasRole('ADMIN')")` on buy/sell/add/remove - a leftover from the pre-multi-tenancy convention ("mutations require ADMIN, matching api.rule's convention") that directly contradicted the whole point of the new USER role. A second test user's buy attempt correctly 500'd with `AuthorizationDeniedException` in the backend log; removed the stale ADMIN gate from both controllers' mutation endpoints (GET/read endpoints were never gated beyond a valid JWT, and stay that way) - a USER account now has full read/write access to its own Portfolio and Watchlist, exactly matching UserAdminController's own doc comment ("role only gates the ADMIN-only endpoints, never portfolio/watchlist access") which had been true in intent but not in the actual PreAuthorize annotations until this fix. Frontend copy ("The shared watchlist"/"The shared portfolio") was also stale from the single-tenant era - updated to "Your watchlist"/"Your portfolio."

Verified live end-to-end with two independent real accounts, not just unit tests: created a second USER account (testuser2@alphagraph.local) via the new admin endpoint, logged into the actual browser UI as both accounts in turn. Admin: added TCS to watchlist, bought 10 RELIANCE (existing 100 TCS holding correctly still present from the pre-multi-tenancy backfill). testuser2: watchlist and portfolio both correctly empty before any action; bought 5 INFY (succeeded only after the ADMIN-gate fix above), added WIPRO to watchlist - both actions correctly auto-recorded a journal entry under testuser2's own userId. Switched back to admin: watchlist/portfolio still showed only TCS/RELIANCE/TCS, no trace of testuser2's INFY/WIPRO. Confirmed directly via psql across all three tables (portfolio_holdings, watchlist_items, trade_journal_entries) that every row's user_id correctly maps to the account that created it - complete two-way isolation, not just "the UI happened to show the right thing." Full `./gradlew build` and `./gradlew test` green throughout, including every existing unit test updated for the new userId-first method signatures across decision.portfolio/watchlist/journal and api.portfolio/watchlist/journal.

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