# 004 — API Architecture

## 1. Purpose

Defines the conventions for the `api` module: the only door into AlphaGraph from `web` or any external caller. Per Module 0.9: REST, OpenAPI/Swagger, JWT, versioning.

## 2. REST Conventions

- **Resource naming**: plural nouns, kebab-case paths — `/api/v1/pipeline-executions`, `/api/v1/rule-definitions`.
- **Versioning**: URI-based, `/api/v1/...`. A breaking change ships as `/api/v2/...` alongside `v1`; `v1` is deprecated (documented, `Deprecation` header) for at least one full phase before removal — never removed in the same phase it's superseded.
- **Pagination**: cursor-agnostic offset paging for Phase 0 (`?page=0&size=50`), response envelope includes `totalElements`, `totalPages`. Large result sets (Phase 1 screening/discovery endpoints) may add cursor pagination later without breaking `v1` — additive.
- **Error envelope**: every non-2xx response returns
  ```json
  { "timestamp": "...", "status": 404, "error": "NOT_FOUND", "message": "...", "path": "/api/v1/..." }
  ```
  via a single `@ControllerAdvice` — no controller hand-rolls its own error shape.
- **DTOs only**: controllers never accept or return JPA entities. Each module's `api` sub-package exposes MapStruct-mapped DTOs; this is also what makes the module dependency rule in [001_System_Architecture §4](001_System_Architecture.md) enforceable — `api` (the module) depends on `<module>.api` (the package), never on `<module>.internal`.

## 3. OpenAPI / Swagger

- Generated via `springdoc-openapi` from controller annotations — the spec is a build artifact, not hand-maintained.
- Served at `/v3/api-docs` and `/swagger-ui.html`, disabled in `prod` profile by default (enabled only behind the admin JWT role if ever needed in production for debugging).
- Every endpoint documents request/response schema, auth requirement, and error responses via annotations — this is a Phase 0 CI check (build fails if a new controller method lacks `@Operation`).

## 4. AuthN / AuthZ

- **JWT bearer tokens**, issued by a `/api/v1/auth/login` endpoint against `platform_users` (see [003_Database_Architecture](003_Database_Architecture.md)).
- Phase 0 roles: `ADMIN` (full CRUD on rules, pipelines, users) and `SYSTEM` (used by the scheduler's own internal calls to `api`, if any — most scheduler work talks to modules directly in-process and does not need this, but it's reserved for cases where an external trigger is needed).
- Tokens are short-lived (1 hour) with no refresh-token flow in Phase 0 — re-login on expiry. Refresh tokens are a Phase 1+ addition once there's a real user base beyond internal admins.
- Method-level authorization via Spring Security `@PreAuthorize`, not manual role checks in controller bodies.
- No consumer-facing auth in Phase 0 — AlphaGraph has no renter/provider-style external user yet; that concept doesn't exist until Phase 3's portfolio/watchlist features need per-user data, at which point this section is revisited.

## 5. Module-to-API Mapping

The `api` module contains one controller package per domain module it exposes (`api.pipeline`, `api.rule`, `api.dataquality`, ...), each depending only on that module's published interfaces/DTOs. This keeps the mapping traceable: any controller's imports show exactly which domain modules it's allowed to touch, and the ArchUnit check from [001_System_Architecture §4](001_System_Architecture.md) applies here too — a controller in `api.rule` importing an internal class from `market` fails the build.

## 6. Phase 0 Endpoints

| Endpoint | Method | Purpose |
|---|---|---|
| `/api/v1/auth/login` | POST | Issue JWT |
| `/api/v1/pipeline-definitions` | GET | List registered pipelines |
| `/api/v1/pipeline-definitions/{id}/run` | POST | Manually trigger a pipeline (admin recovery path — the only sanctioned manual execution, per [002_Engine_Architecture §6](002_Engine_Architecture.md)) |
| `/api/v1/pipeline-executions` | GET | List runs, filterable by pipeline/status/date |
| `/api/v1/pipeline-executions/{id}` | GET | Run detail incl. errors and data quality score |
| `/api/v1/rule-definitions` | GET/POST | List/create rules |
| `/api/v1/rule-definitions/{id}/activate` | POST | Activate a rule version (deactivates the prior active version of the same name) |
| `/actuator/health` | GET | Liveness/readiness, unauthenticated |
| `/actuator/prometheus` | GET | Metrics scrape endpoint, network-restricted (see [005_Deployment](005_Deployment.md)) |

No `market`/`financial`/`ownership` read endpoints exist yet — those arrive in Phase 1 once the modules that own the data exist.
