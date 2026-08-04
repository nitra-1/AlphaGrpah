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

Next: News Engine (last of Phase 2's four planned engines) or Phase 2's 2B/2C document-collector expansion (dedicated Conference Call transcript / Investor Presentation / Press Release collectors, beyond what Exchange Announcements already bring in via Module 2.1) - neither started yet, next decision point. Also still explicitly deferred, not started: retroactively applying the Observation+Historical Ledger two-layer pattern to Phase 1's already-complete engines (Technical/Fundamental/Institutional/Sector/Risk) - out of scope unless the user asks for it as a separate, deliberate decision.

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