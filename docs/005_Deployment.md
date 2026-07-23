# 005 — Deployment Architecture

## 1. Purpose

Defines how AlphaGraph runs — locally and in CI now, cloud later — plus the security posture that governs secrets, network boundaries, and access control (folded in here rather than as a separate document, per the Phase 0 artifact list). Per Module 0.2: cloud can wait; Docker Compose is the target for Phase 0.

## 2. Environments

| Environment | Purpose | Profile |
|---|---|---|
| `local` | Developer machine, Docker Compose | `local` |
| `ci` | GitHub Actions, ephemeral containers | `ci` |
| `docker` | Single-host deployment (staging-equivalent until cloud is introduced) | `docker` |
| `prod` | Reserved; not used until a real cloud target is chosen post-Phase-0 | `prod` |

Spring profiles select datasource URLs, log verbosity, and which auxiliary features are enabled (e.g. Swagger UI on in `local`/`ci`, off in `docker`/`prod`).

## 3. Docker Compose (Phase 0 target)

Services:

| Service | Image | Purpose |
|---|---|---|
| `app` | built from repo `Dockerfile` (multi-stage: Gradle build → JRE runtime) | The Spring Boot monolith |
| `postgres` | `postgres:16` | Primary datastore, one instance, multiple schemas per [003_Database_Architecture](003_Database_Architecture.md) |
| `redis` | `redis:7` | Caching + rate-limit counters for `api` |
| `prometheus` | `prom/prometheus` | Scrapes `app:/actuator/prometheus` |
| `grafana` | `grafana/grafana` | Dashboards over Prometheus data source |

Kafka is deferred (Module 0.2 marks it "future") — not part of Phase 0 Compose; introduced when a module needs async event fan-out (likely Phase 2 corporate events or Phase 4 learning feedback loops).

`docker-compose.yml` at repo root; `.env` (git-ignored) supplies `POSTGRES_PASSWORD`, `JWT_SECRET`, etc. — `.env.example` checked in with placeholder values documents what's required.

## 4. CI/CD — GitHub Actions

Pipeline stages, in order, on every PR:

1. **Build** — `./gradlew build` (compiles all modules).
2. **Lint/Static analysis** — Checkstyle/Spotless + the ArchUnit module-boundary test suite from [001_System_Architecture §4](001_System_Architecture.md).
3. **Test** — unit tests per module + Flyway migration tests (spin up a throwaway Postgres via Testcontainers, run every module's migrations from scratch).
4. **Docker build** — build the `app` image; on `main` only, also push to the registry, tagged with the commit SHA.

Merges to `main` require all four stages green. No stage is skipped via `--no-verify`-equivalent for this project.

## 5. Observability

- **Metrics**: Spring Actuator + Micrometer expose `/actuator/prometheus`; Prometheus scrapes every 15s. Key Phase 0 metrics: pipeline execution duration/status counts, JVM/HTTP standard metrics.
- **Dashboards**: Grafana, provisioned from JSON checked into `infra/grafana/dashboards/` — a "Pipeline Health" board (last run status per pipeline, rows processed, data quality score trend) is the first Phase 0 dashboard, since Module 0.10 logging is otherwise invisible without it.
- **Logging**: structured JSON logs (Logback + `logstash-logback-encoder`) to stdout, one line per event, correlation id (`X-Request-Id`) propagated from `api` through to pipeline executions triggered by an API call, so a run can be traced back to the request that triggered it.

## 6. Security Architecture

- **Secrets**: never in source or Docker images. Local/`docker` env via `.env` (git-ignored); CI via GitHub Actions encrypted secrets. Cloud secrets manager is a decision deferred to whichever cloud target is chosen post-Phase-0.
- **Database access**: least privilege — the `app` connects as a role with DML rights on its schemas only, no `SUPERUSER`; migrations run as a separate, more privileged role used only at deploy time, not by the running application.
- **Network**: in Compose, only `app` (and `grafana`, for the operator) publish ports to the host; `postgres`, `redis`, `prometheus` are internal-network-only. `/actuator/prometheus` is not exposed publicly — scraped over the internal Compose network.
- **JWT secret rotation**: `JWT_SECRET` is a single symmetric key in Phase 0 (HS256); rotation is a manual redeploy-with-new-secret (invalidates all sessions) until token volume justifies a rotation scheme with overlapping keys.
- **Transport**: TLS termination is out of scope for `local`/`docker` (plain HTTP inside the Compose network); required at whichever reverse proxy/load balancer sits in front once a real environment exists.
- **Dependency hygiene**: Dependabot (or equivalent) enabled on the repo from day one — a foundation-phase project is the cheapest time to start this habit, not something to bolt on later.

## 7. Config Management

- All environment-specific values externalized via Spring `application-<profile>.yml` + environment variables — no profile-specific values hard-coded in `application.yml` (the base file holds only profile-agnostic defaults).
- Feature toggles (e.g. "is the Python NLP sidecar available") are plain boolean config properties, not a dedicated feature-flag system — not justified at Phase 0 scale.
