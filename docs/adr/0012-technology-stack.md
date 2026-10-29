---
status: accepted
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack follow-up)
---

# ADR-0012: Technology stack — Java 21 + Spring Boot, React + Vite + TypeScript, PostgreSQL 16

## Context and Problem Statement

The Architecture Pack fixed the architectural style (modular monolith,
[ADR-0001](0001-modular-monolith.md)), the API style
([ADR-0002](0002-rest-json-api-openapi.md)) and a single relational database
([ADR-0003](0003-single-relational-database.md)), but left the concrete
technology stack open (`constraints.md`, pending decisions). The
non-functional targets have now been quantified (see
[`requirements.md`](../requirements.md)), so the stack can be chosen against
measurable requirements.

Which backend language/framework, frontend framework, database engine, data
access and migration tools, test tooling and build tool should the Matriz MVP
use so that it meets the functional and non-functional requirements with the
least delivery risk for a small team?

Scope: the whole MVP stack (backend, SPA, database, persistence, tests,
build). Hosting/deployment environment is **not** in scope (still pending).

## Decision Drivers

Quantified non-functional requirements (confirmed by the user):

* **DD1 — Load:** 5,000 users, all of whom may be concurrent (same
  organization, same working hours). Estimated peak ~300–500 requests/s,
  including notification polling every 30–60 s (5,000 / 60 s ≈ 85 req/s to
  5,000 / 30 s ≈ 170 req/s from polling alone).
* **DD2 — Latency (p95, server-side, excluding network):** < 200 ms for lists
  and dashboard; < 500 ms for writes.
* **DD3 — Data volume after year 1:** ~5k projects, ~500k tasks, ~2M comments.
* **DD4 — Growth:** ~2× in 2 years (≈ 10,000 users).
* **DD5 — Availability:** ~99% during business hours; single instance, single
  region; brief downtime for deploys is acceptable. No real-time updates —
  refresh/polling is fine.
* **DD6 — Global users:** users work across regions, so dates ("today",
  overdue, deadline approaching) are evaluated in each **user's time zone**.

Decisions already made that the stack must implement:

* **DD7 — Modular monolith with enforceable boundaries**
  ([ADR-0001](0001-modular-monolith.md)).
* **DD8 — REST/JSON with an OpenAPI 3.1 contract**
  ([ADR-0002](0002-rest-json-api-openapi.md)) and `problem+json` errors
  ([ADR-0010](0010-uniform-error-format-problem-json.md)).
* **DD9 — Consistency:** status + history in one ACID transaction
  ([ADR-0008](0008-status-change-and-history-in-one-transaction.md));
  optimistic concurrency with `version`
  ([ADR-0005](0005-optimistic-concurrency-for-tasks.md)).
* **DD10 — Security** ([ADR-0009](0009-authentication-server-side-sessions.md)):
  server-side sessions in an `HttpOnly` cookie, session ID rotation, CSRF
  protection, Argon2id/bcrypt hashing, rate limiting/lockout, server-side
  authorization; no sensitive data in logs/errors.

Project constraints:

* **DD11 — Simplicity / deliverability:** the MVP must stay "simple, usable,
  and deliverable"; small team; least custom code for cross-cutting concerns.
* **DD12 — Clean-clone reproducibility:** a fresh clone must build and run
  with tools already available locally (Docker 28, Java 21, Node 25); no
  extra toolchains.
* **DD13 — Evolution:** moving to multiple instances or extracting a module
  later should be a small change.

## Considered Options

Backend (language + framework):

* **B1** — Java 21 + Spring Boot
* **B2** — Rust + Axum (sqlx, tower-sessions, axum-csrf, utoipa)
* **B3** — TypeScript (Node) + Fastify (session/CSRF/rate-limit plugins,
  Prisma or Drizzle)
* **B4** — Python + FastAPI (SQLAlchemy, Alembic, pytest)

Frontend:

* **F1** — React + Vite + TypeScript SPA
* **F2** — Angular
* **F3** — Vue + Vite + TypeScript
* **F4** — Server-rendered pages (Thymeleaf)

Database engine:

* **D1** — PostgreSQL 16 (Docker Compose)
* **D2** — SQLite

Data access and migrations (Java backend):

* **P1** — Spring Data JPA (Hibernate) with DTO projections / fetch joins for
  reads
* **P2** — jOOQ
* **P3** — Hybrid: JPA for writes, Spring JDBC for reads
* **M1** — Flyway (plain SQL migrations)
* **M2** — Liquibase

Tests:

* **T1** — Testcontainers with a real PostgreSQL (+ k6 for load)
* **T2** — H2 in-memory database

Build:

* **K1** — Maven (with wrapper `./mvnw`)
* **K2** — Gradle (Kotlin DSL)

## Decision Outcome

Chosen options: **B1** Java 21 + Spring Boot, **F1** React + Vite +
TypeScript, **D1** PostgreSQL 16, **P1** Spring Data JPA + DTO projections,
**M1** Flyway, **T1** Testcontainers + k6, **K1** Maven — because the
workload is database-bound, so every backend option meets DD1/DD2 on a single
instance, and the deciding factors become security (DD10), enforceable module
boundaries (DD7), consistency (DD9), delivery risk (DD11) and reproducibility
(DD12), where this combination needs the least custom code:

* **Spring Security** covers ADR-0009 almost entirely by configuration:
  session fixation protection (ID rotation), CSRF tokens, Argon2id password
  encoder, server-side authorization (`@PreAuthorize`/method security),
  sign-out invalidation.
* **Spring Session JDBC** stores sessions in PostgreSQL (no Redis), and makes
  moving to Redis or to multiple instances a configuration change (DD13).
* **Spring Modulith** verifies module boundaries in a test (DD7) and eases
  later extraction (e.g. Notifications).
* **JPA `@Version`** implements optimistic concurrency (ADR-0005);
  `@Transactional` gives status + history atomicity (ADR-0008); Spring's
  `ProblemDetail` implements `problem+json` (ADR-0010).
* **Virtual threads** (Java 21) and **HikariCP** pooling handle hundreds of
  concurrent blocking DB-bound requests without reactive code (DD1).
* **`@Scheduled`** runs the deadline-approaching job inside the same
  deployable (FR11).
* **PostgreSQL** is the only considered engine that sustains thousands of
  concurrent sessions with concurrent writes (DD1, DD3) and supports the
  needed indexes (trigram / functional).
* **React + Vite + TypeScript** with **TanStack Query** covers
  loading/error/empty states and notification polling (`refetchInterval`),
  with a typed API client generated from `openapi.yaml` (DD8) and
  `Intl`/`date-fns-tz` for per-user time zones (DD6).

Summary of the chosen stack:

| Layer | Choice |
|-------|--------|
| Backend | Java 21, Spring Boot (Spring Web MVC, Spring Security, Spring Session JDBC, Spring Modulith, `@Scheduled`), virtual threads, HikariCP |
| Persistence | Spring Data JPA (Hibernate) with `@Version`; DTO projections / fetch joins on read paths; Flyway plain-SQL migrations |
| Database | PostgreSQL 16 via Docker Compose; also stores HTTP sessions |
| Frontend | React + Vite + TypeScript SPA; TanStack Query; typed client generated from `openapi.yaml`; `Intl` / `date-fns-tz` |
| Tests | JUnit 5, Spring Boot Test, MockMvc, Testcontainers (PostgreSQL); Vitest + React Testing Library; k6 smoke/load test |
| Build | Maven with wrapper (`./mvnw`); npm for the SPA |

### Consequences

* Good, because every security requirement of ADR-0009 maps to a
  well-maintained framework feature instead of hand-assembled middleware.
* Good, because module boundaries (ADR-0001) are checked automatically, which
  protects maintainability as features grow.
* Good, because a single PostgreSQL instance serves data and sessions — one
  stateful dependency, started with `docker compose up`.
* Good, because the expected load (≤ 500 req/s, mostly indexed reads) is far
  below what one Spring Boot instance with a ~20-connection pool can serve,
  leaving headroom for 2× growth (DD4).
* Good, because the API contract stays the single source of truth: backend
  is checked against `openapi.yaml`, and the frontend client is generated
  from it.
* Bad, because there are two languages (Java and TypeScript); types are
  shared only through OpenAPI code generation.
* Bad, because Java/Spring has more boilerplate, slower builds and startup,
  and a higher memory footprint (~300 MB) than Rust or Node — acceptable for a
  single long-running instance (DD5).
* Bad, because JPA can produce N+1 queries or over-fetching if used naively;
  read paths must use DTO projections / fetch joins.
* Neutral, because each request reads the session from PostgreSQL (primary-key
  lookup); at ≤ 500 req/s this is negligible, and the session store can move
  to Redis later by configuration.
* Neutral, because per-user time zones (DD6) require a time-zone attribute on
  the user (IANA zone ID) — a data-model change still to be added to
  [`database.md`](../database.md) — and the deadline job must run periodically
  (e.g. hourly) and evaluate "today/tomorrow" in each recipient's time zone;
  the existing uniqueness rule keeps it idempotent.
* Neutral, because indexes are needed to keep reads within DD2 at DD3 volumes:
  `(project_id, status)` on tasks, a trigram or `lower(name)` index for
  project-name search, an index for comments by task, and a cheap
  unread-notification-count query for polling.

### Confirmation

* **Module boundaries:** a Spring Modulith verification test
  (`ApplicationModules.of(...).verify()`) runs in every build and fails on
  forbidden cross-module dependencies.
* **Persistence fidelity:** integration tests run against PostgreSQL 16 via
  Testcontainers in CI (no H2), covering Flyway migrations, optimistic-locking
  `409`, and status + history atomicity.
* **Security:** MockMvc/Spring Security tests for session rotation, sign-out
  invalidation, CSRF rejection, lockout, and authorization (non-disclosing
  `404`).
* **Performance:** a k6 smoke/load test with seeded year-1 volumes (DD3)
  asserts p95 < 200 ms for list/dashboard endpoints and < 500 ms for writes
  at the target peak rate (DD1/DD2).
* **Reproducibility:** a fresh clone runs with `docker compose up` (database),
  `./mvnw verify`, and `npm ci && npm test` using only Docker, Java 21 and
  Node.

## Pros and Cons of the Options

Legend for tables: ✅ meets the driver well · ⚠️ meets it with extra work or
risk · ❌ does not meet it.

### Backend

| Driver | B1 Java 21 + Spring Boot | B2 Rust + Axum | B3 TS + Fastify | B4 Python + FastAPI |
|--------|--------------------------|----------------|-----------------|---------------------|
| DD1/DD2 load & latency (DB-bound) | ✅ virtual threads + pool | ✅ highest headroom | ✅ (one event loop per process) | ⚠️ slowest runtime, still enough with workers |
| DD4 growth to 10k users | ✅ | ✅ | ✅ (scale by processes) | ⚠️ |
| DD7 enforceable modules | ✅ Spring Modulith | ⚠️ crates/modules by convention | ⚠️ lint rules by convention | ⚠️ import rules by convention |
| DD8 OpenAPI / problem+json | ✅ springdoc, `ProblemDetail` | ✅ utoipa (manual problem type) | ✅ schema plugins | ✅ generated OpenAPI |
| DD9 transactions / `@Version` | ✅ `@Transactional`, `@Version` | ⚠️ manual version check (sqlx) | ⚠️ manual or ORM-specific | ✅ SQLAlchemy `version_id_col` |
| DD10 sessions, CSRF, Argon2id, lockout | ✅ Spring Security + Spring Session | ⚠️ younger crates, more glue | ⚠️ hand-assembled plugins | ⚠️ cookie sessions/CSRF not first-class |
| DD11 delivery speed / simplicity | ⚠️ boilerplate, but conventions | ❌ slowest (≈2–3× code for CRUD) | ✅ fastest | ✅ fast |
| DD12 clean clone (Java 21, Node 25, Docker) | ✅ | ❌ toolchain not installed | ✅ | ⚠️ Python env to manage |
| DD13 multi-instance later | ✅ config change (Spring Session) | ⚠️ | ⚠️ | ⚠️ |
| Languages in the repo | 2 (Java + TS) | 2 (Rust + TS) | 1 (TS) | 2 (Python + TS) |

#### B1 — Java 21 + Spring Boot (chosen)

* Good, because Spring Security covers ADR-0009 (session fixation, CSRF,
  Argon2id, method-level authorization, sign-out) with the least custom code
  (DD10).
* Good, because Spring Modulith enforces ADR-0001 boundaries in a test and
  eases later extraction (DD7, DD13).
* Good, because `@Version` and `@Transactional` implement ADR-0005 and
  ADR-0008 directly (DD9).
* Good, because Spring Session JDBC keeps sessions in PostgreSQL now and makes
  Redis / multiple instances a configuration change later (DD13).
* Good, because virtual threads let simple blocking code serve hundreds of
  concurrent DB-bound requests (DD1, DD2).
* Good, because Java 21 is already installed (DD12) and the ecosystem is
  mature and widely known.
* Neutral, because the workload is DB-bound, so its raw performance is
  "enough" rather than a differentiator.
* Bad, because of more boilerplate and slower builds/startup than B2/B3.
* Bad, because of higher memory usage (~300 MB) — irrelevant for one
  long-running instance (DD5).
* Bad, because the repo has two languages; types are shared via OpenAPI
  codegen.

#### B2 — Rust + Axum (sqlx, tower-sessions, axum-csrf, utoipa)

* Good, because it has the best raw performance and memory (~20 MB, instant
  startup, typically 2–5× the throughput) — headroom DD1/DD4 do not need.
* Good, because sqlx checks SQL at compile time and the type system prevents
  many bugs.
* Neutral, because utoipa can produce the OpenAPI document (DD8).
* Bad, because delivery is slowest: roughly 2–3× more code for CRUD (DD11).
* Bad, because session/CSRF crates are younger and need more glue to meet
  ADR-0009 (DD10).
* Bad, because module boundaries and optimistic locking are by convention /
  manual (DD7, DD9).
* Bad, because the Rust toolchain is not installed locally (DD12).

#### B3 — TypeScript (Node) + Fastify

* Good, because delivery is fastest and the team uses one language with
  natively shared types with the SPA (DD11).
* Good, because Node 25 is already installed (DD12).
* Neutral, because Prisma/Drizzle provide migrations and typed queries.
* Bad, because security is hand-assembled from separate plugins (session,
  `csrf-csrf`, rate-limit, password hashing) — more places to get ADR-0009
  wrong (DD10).
* Bad, because a single event loop per process means CPU-heavy work (e.g.
  dashboard aggregation in code) blocks other requests; scaling is by
  processes (DD2).
* Bad, because module boundaries and optimistic locking are by convention /
  manual (DD7, DD9).

#### B4 — Python + FastAPI (SQLAlchemy, Alembic, pytest)

* Good, because OpenAPI and Pydantic validation come for free (DD8).
* Good, because it is quick to write and SQLAlchemy supports version columns
  (DD9).
* Bad, because cookie sessions and CSRF are not first-class; they need
  third-party or custom code (DD10).
* Bad, because it is the slowest runtime of the options (less headroom for
  DD2/DD4).
* Bad, because module boundaries are by convention (DD7) and a Python
  environment adds setup to a clean clone (DD12).
* Bad, because the repo has two languages.

### Frontend

| Driver | F1 React + Vite + TS | F2 Angular | F3 Vue + Vite + TS | F4 Thymeleaf (SSR) |
|--------|----------------------|------------|--------------------|--------------------|
| SPA per ADR-0002 / architecture §4 | ✅ | ✅ | ✅ | ❌ contradicts SPA |
| DD5 polling, loading/error/empty states | ✅ TanStack Query | ✅ RxJS / HttpClient | ✅ TanStack Query (Vue) | ⚠️ full page reloads |
| DD8 typed client from OpenAPI | ✅ | ✅ | ✅ | n/a |
| DD10 CSRF with Spring | ⚠️ small fetch wrapper | ✅ built-in `XSRF-TOKEN` | ⚠️ small wrapper | ✅ built-in |
| DD6 per-user time zone | ✅ `Intl`, `date-fns-tz` | ✅ | ✅ | ✅ |
| DD11 delivery / simplicity | ✅ | ⚠️ more boilerplate | ✅ simplest | ✅ (but see above) |
| Ecosystem / hiring | ✅ largest | ✅ | ⚠️ smaller | ⚠️ |

#### F1 — React + Vite + TypeScript (chosen)

* Good, because TanStack Query handles loading/error/empty states, caching and
  notification polling via `refetchInterval` (DD5, FR11).
* Good, because a typed client generated from `openapi.yaml` keeps the SPA in
  sync with the contract (DD8).
* Good, because it has the largest ecosystem and Vite gives fast builds; Node
  25 is installed (DD12).
* Good, because `Intl` / `date-fns-tz` render dates in the user's time zone
  (DD6).
* Bad, because router, forms and UI kit must be chosen by the team.
* Bad, because CSRF needs a small fetch wrapper that sends the token header
  (DD10).

#### F2 — Angular

* Good, because built-in CSRF handling (`XSRF-TOKEN` cookie → header) matches
  Spring Security's defaults (DD10).
* Good, because it is opinionated (router, forms, DI included).
* Bad, because of a heavier bundle and more boilerplate, slowing the MVP
  (DD11).

#### F3 — Vue + Vite + TypeScript

* Good, because it is the simplest to learn and has the smallest bundle.
* Neutral, because TanStack Query is also available for Vue.
* Bad, because its ecosystem is smaller than React's.
* Bad, because CSRF also needs a small wrapper (DD10).

#### F4 — Server-rendered pages (Thymeleaf)

* Good, because it has one language and CSRF tokens are built into forms.
* Bad, because it contradicts the SPA + REST/JSON design (ADR-0002,
  `architecture.md` §4). Rejected.

### Database engine

| Driver | D1 PostgreSQL 16 | D2 SQLite |
|--------|------------------|-----------|
| DD1 5k concurrent sessions + concurrent writes | ✅ MVCC, many concurrent writers | ❌ single writer |
| DD2/DD3 indexed reads at 500k tasks / 2M comments | ✅ trigram / functional / composite indexes | ⚠️ fewer index types |
| DD9 ACID, row-level concurrency | ✅ | ⚠️ database-level write lock |
| Session store (ADR-0009) | ✅ Spring Session JDBC | ❌ writes on every request contend |
| DD12 clean clone | ✅ Docker Compose (Docker 28 installed) | ✅ no Docker needed |
| Test fidelity (prod = test engine) | ✅ Testcontainers | ⚠️ |

#### D1 — PostgreSQL 16 via Docker Compose (chosen)

* Good, because MVCC sustains thousands of concurrent sessions with concurrent
  reads and writes (DD1).
* Good, because it supports the indexes the targets need (DD2, DD3): trigram
  (`pg_trgm`) or `lower(name)` for project search, `(project_id, status)` on
  tasks, partial index for unread notifications.
* Good, because it can also store HTTP sessions, so no Redis is needed.
* Good, because the same engine runs in tests (Testcontainers) and locally
  (Docker Compose, DD12).
* Bad, because it requires Docker for local development.

#### D2 — SQLite

* Good, because it needs no Docker and gives the simplest clean clone (DD12).
* Bad, because a single writer cannot sustain 5,000 concurrent sessions plus
  task/comment writes (DD1).
* Bad, because of weaker fidelity with a server database and fewer index
  options (DD2, DD3).

### Data access and migrations

| Driver | P1 Spring Data JPA + projections | P2 jOOQ | P3 JPA writes + JDBC reads |
|--------|----------------------------------|---------|----------------------------|
| DD9 optimistic locking | ✅ `@Version` | ⚠️ manual version check | ✅ (writes via JPA) |
| DD2 predictable read performance | ⚠️ needs projections / fetch joins | ✅ explicit SQL | ✅ |
| DD11 CRUD code volume | ✅ least | ⚠️ more | ⚠️ two styles |
| Consistency of codebase | ✅ one style | ✅ one style | ❌ two styles |

#### P1 — Spring Data JPA + DTO projections (chosen)

* Good, because `@Version` implements ADR-0005 with no custom code (DD9).
* Good, because CRUD repositories minimize code (DD11).
* Neutral, because read paths must use DTO projections / fetch joins to avoid
  N+1 and keep p95 < 200 ms (DD2).
* Bad, because ORM behaviour (lazy loading, flush) must be understood.

#### P2 — jOOQ

* Good, because SQL is type-safe and predictable.
* Bad, because of more CRUD code and a manual version check for ADR-0005.

#### P3 — Hybrid (JPA writes, Spring JDBC reads)

* Good, because it combines `@Version` with hand-tuned reads.
* Bad, because two persistence styles increase cognitive load.

#### M1 — Flyway (chosen)

* Good, because migrations are plain SQL, so PostgreSQL-specific indexes
  (trigram, partial) are written directly.
* Good, because it is simple and well integrated with Spring Boot.
* Bad, because it has fewer features (e.g. no generic rollback) than
  Liquibase.

#### M2 — Liquibase

* Good, because it is more powerful (changelogs, rollbacks, multi-format).
* Bad, because it is more verbose; the extra power is not needed for one
  engine.

### Tests

#### T1 — Testcontainers with real PostgreSQL + k6 (chosen)

* Good, because integration tests run on the production engine: `ILIKE` /
  trigram search, locking, Flyway SQL and session tables behave as in
  production (DD9, DD10).
* Good, because k6 measures p95 against the targets (DD2).
* Bad, because tests are slower and require Docker (available, DD12).

#### T2 — H2 in-memory

* Good, because tests are faster and need no Docker.
* Bad, because it is not faithful for `ILIKE`/trigram, locking behaviour and
  PostgreSQL-specific Flyway SQL — bugs would surface only in production.

### Build

#### K1 — Maven with wrapper (chosen)

* Good, because it is the most common and declarative Java build; the wrapper
  pins the version for a clean clone (DD12).
* Bad, because it is less flexible and slower than Gradle.

#### K2 — Gradle (Kotlin DSL)

* Good, because builds are faster (incremental, build cache) and more flexible.
* Bad, because it is more complex to configure and maintain (DD11).

## More Information

Capacity sketch (estimate, to be confirmed by the k6 test): at 500 req/s with
an average server time of ~50 ms, about 25 requests are in flight (Little's
law); with ~5–10 ms of database time per request, only ~3–5 connections are
busy on average, so a pool of ~20 connections and one instance leave headroom
for the 2× growth (DD4).

Revisit this decision if:

* the k6 test shows p95 above the targets after indexing and query tuning;
* availability requirements exceed a single instance (then: second instance +
  Spring Session in Redis or PostgreSQL, load balancer);
* real-time updates become a requirement (then: SSE/WebSocket).

Related decisions: [ADR-0001](0001-modular-monolith.md),
[ADR-0002](0002-rest-json-api-openapi.md),
[ADR-0003](0003-single-relational-database.md),
[ADR-0005](0005-optimistic-concurrency-for-tasks.md),
[ADR-0008](0008-status-change-and-history-in-one-transaction.md),
[ADR-0009](0009-authentication-server-side-sessions.md),
[ADR-0010](0010-uniform-error-format-problem-json.md).
Requirements: [`requirements.md`](../requirements.md),
[`constraints.md`](../constraints.md).
