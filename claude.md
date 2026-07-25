Module 0.1 status: DONE (docs/001-005 written, committed, pushed)
Module 0.2 status: DONE (tech stack below; Python NLP/ML sidecar added for corporate/learning modules, Phase 2+)
Module 0.3 status: DONE (15-module Gradle scaffold matching the module map below, ArchUnit module-boundary test in bootstrap, verified: ./gradlew build passes and the app boots)
Module 0.4 status: DONE (Flyway migrations for common/reference/scheduler/api schemas per docs/003_Database_Architecture.md, verified against a real local Postgres 17 instance - schemas, tables, indexes, constraints, seed data, and the updated_at trigger all confirmed working)
Module 0.5 status: DONE (Collector/Parser/Validator/Normalizer/Loader + Pipeline orchestrator + RequiredFieldsValidator in the common module, 5 passing unit tests covering success/partial/failure paths)
Module 0.6 status: DONE (DataQualityEngine in common.quality - DataQualitySpec/DataQualityInput/DataQualityScore + equal-weight placeholder formula, 8 passing unit tests, kept independent of both the ETL framework and any domain module - scheduler wires the two together in Module 0.8)
Module 0.7 status: DONE (RuleEvaluator in common.rules - Rule/RuleCondition/MetricContext/EvaluationResult + ArithmeticRuleEvaluator covering all 6 operators, 9 passing unit tests. Also fixed a real V1 schema gap: rule_conditions needed a second upper_bound column for BETWEEN, added via V2 migration, verified against real Postgres)
Module 0.8 status: DONE (PipelineOrchestrator in the scheduler module wires Pipeline + DataQualityEngine + quality gate + NullEngine.calculate + Notify, per docs/002_Engine_Architecture.md §6. Persists to scheduler.pipeline_executions/pipeline_execution_errors and common.data_quality_scores via JdbcPipelineExecutionRecorder, behind a PipelineExecutionRecorder interface so it's unit-testable without a DB. DailyPipelineScheduler runs a dummy proof pipeline on a 6PM IST cron plus on demand. Verified end-to-end against real Postgres: SUCCESS row + 1.0 quality score correctly linked)
Module 0.9 status: NOT STARTED (API Layer - next up)

Local dev environment notes (for resuming without rediscovery):
- Repo: https://github.com/nitra-1/AlphaGrpah (main branch)
- JDK 17 at D:\java runs Gradle itself; Gradle 8.11 at D:\gradle; GRADLE_USER_HOME set to D:\gradle-home (space-free path)
- The project's Java 21 toolchain is auto-provisioned by Gradle (foojay-resolver-convention in settings.gradle.kts) into D:\gradle-home\jdks - no manual JDK 21 install needed
- Windows quirk: this machine's username has a space in it, which breaks the JDK's internal loopback socket (java.nio Selector) unless JDK_JAVA_OPTIONS=-Djdk.net.unixdomain.tmpdir=D:/tmp is set. Already set as a permanent user env var, so new shells pick it up automatically
- A standalone local PostgreSQL 17 test instance runs on port 5433 (data dir D:\pgdata, role alphagraph_app / alphagraph_local_dev, db "alphagraph") - separate from docker-compose.yml's port-5432 setup, used only for manual verification
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