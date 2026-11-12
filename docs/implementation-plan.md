# Matriz — Implementation Plan (release 1)

Executable plan for release 1, the first full-stack implementation of the
Matriz MVP. It reconciles the approved specification and architecture
documentation with the release 1 scope, consolidates access rules, and splits
the work into increments 2–9. **Update the Status column and the change log at the end of
every increment.**

Sources: [`AGENTS.md`](../AGENTS.md), [`README.md`](../README.md),
[`requirements.md`](requirements.md), [`user-stories.md`](user-stories.md),
[`constraints.md`](constraints.md), [`architecture.md`](architecture.md),
[`adr/`](adr/README.md), [`database.md`](database.md),
[`openapi.yaml`](../openapi.yaml), [`ui/wireframe.png`](ui/wireframe.png).

Labels used below:
- **Confirmed** — decided with the user acting as client representative.
- **Required** — mandatory release 1 requirement (see `requirements.md`).
- **Proposed** — decided by the agent within the scope delegated by the user
  ("make the decisions you find reasonable"); can be revisited.
- **Deferred** — kept in the docs for a later phase; not implemented now.

## 1. Status overview

| # | Increment | Status |
|---|-----------|--------|
| 1 | Consolidated plan, access rules and API contract | Done |
| 2 | Application skeleton, persistence, migrations and demo seed | Done |
| 3 | Authentication and server-side authorization | Done |
| 4 | Project management (incl. archive/unarchive, members) | Done |
| 5 | Task management | Done |
| 6 | Assignees and comments | Done |
| 7 | Frontend completion (dashboard, layout, states, sync) | Done |
| 8 | MVP verification (`docs/verification.md`, tests) | Done |
| 9 | Review and delivery | Done |

Status values: Planned → In progress → Done (or Blocked, with a reason in the
change log).

Process for every increment (Confirmed): analysis and plan → user approval →
implement → run checks → review the diff → commit
a commit with a clear message describing the increment (no push).

## 2. Scope reconciliation (release 1 vs approved MVP)

| Area | Approved MVP specification | Release 1 requirement | Resolution |
|------|---------------------------|----------------|------------|
| Authentication | FR1 sign-in; account management | Sign in/out, current user; seeded demo accounts; registration and password reset optional | Seeded local accounts only; no registration, password reset or admin UI (Confirmed). |
| Projects | Create, edit, **archive** (ADR-0007) | Create, list, read, edit, **delete** | **Archive/unarchive instead of delete** (Confirmed): UI confirmation, dependents kept, project read-only. Recorded difference. |
| Project fields | name, description, owner, members, status, start/end dates | name 3–100, description ≤ 1000 | name, description, owner (shown), status (Confirmed); dates **Deferred**. |
| Tasks | Create/edit/assign; status, priority, deadline | Create, read, edit, **delete**; status, priority | Hard delete by Managers with cascade (Confirmed, ADR-0014); deadline **Deferred**. |
| Assignees | Single owner per task | Several eligible users per task; remove each; no duplicates | N:M `task_assignee` (Confirmed, ADR-0013); 0..N at creation. |
| Comments | Any member comments | Add and list, author and date, 1–2000 | Confirmed; immutable; no comments on archived projects. |
| Search & filters | Filter/sort by status, priority, owner, deadline | Search projects by name; filter tasks by status and priority | Project name search; task filters status, priority, assignee (Proposed); deadline filters **Deferred**. |
| Dashboard | Per-project progress, overdue, unassigned | Accessible projects + task counts per status, navigation | `GET /dashboard` (Confirmed, ADR-0015); overdue **Deferred**. |
| Status history | FR10 | — | **Deferred** (ADR-0008). |
| Deadlines / overdue | FR5 deadline, FR9, exceptional case 1 | — | **Deferred** (ADR-0011). |
| Notifications | FR11 in-app | — | **Deferred** (incl. scheduler job). |
| Concurrency | Optimistic `version`, `409` | — | Kept (Confirmed, ADR-0005). |
| Brute force | Rate limiting / lockout | — | Kept (Confirmed, ADR-0009). |
| UI language | — | PT translation allowed | English throughout (Confirmed); API enum values unchanged. |

## 3. Confirmed technology stack

From [ADR-0012](adr/0012-technology-stack.md) (accepted, confirmed):
Java 21 + Spring Boot (Web MVC, Spring Security, Spring Session JDBC, Spring
Modulith), Maven wrapper; React + Vite + TypeScript, TanStack Query, client
generated from `openapi.yaml`; PostgreSQL 16 via Docker Compose (sessions in
PostgreSQL, no Redis); Spring Data JPA with DTO projections; Flyway; JUnit 5,
Spring Boot Test, MockMvc, Testcontainers; Vitest + React Testing Library.
Local toolchain: Java 21, Node 25, Docker 28 (no `psql` CLI — use
`docker compose exec db psql`).

Technical details ([ADR-0016](adr/0016-contract-first-api-and-generated-client.md)):
- Repository layout: `backend/` (Maven, base package `com.matriz`),
  `frontend/` (Vite), `docker-compose.yml` and `.env.example` at the root.
- Ports: SPA 5173, API 8080, PostgreSQL 5432 (overridable in `.env`). The Vite
  dev server proxies `/api` to the backend → same origin, no CORS.
- Contract-first: `openapi.yaml` is the source of truth, linted with Redocly
  (`redocly.yaml`). Frontend types via `openapi-typescript`, calls via
  `openapi-fetch`; drift check = regenerate + `git diff --exit-code`.
  Backend responses validated against `openapi.yaml` in MockMvc tests.
- Spring Modulith verification test: **in scope** (Increment 2). k6 load test,
  `@Scheduled` job and per-user time zone: **Deferred**.
- Per-IP sign-in rate limit: in-memory counter (single instance, D8).

## 4. Access rules (consolidated)

Roles: **Admin** = platform Administrator (`is_admin`); **Manager** /
**Member** = per-project role; **Assignee** = member assigned to the task;
**Outsider** = signed-in user who is not a member of the project.

| Action | Admin | Manager | Assignee | Member | Outsider |
|--------|:-----:|:-------:|:--------:|:------:|:--------:|
| Create a project (becomes owner + Manager) | ✅ | ✅ | ✅ | ✅ | ✅ |
| List/search/read project, dashboard entry | ✅ all | ✅ | ✅ | ✅ | 404 |
| Edit project name/description | ✅ | ✅ | 403 | 403 | 404 |
| Archive / unarchive project | ✅ | ✅ | 403 | 403 | 404 |
| List members (= eligible assignees) | ✅ | ✅ | ✅ | ✅ | 404 |
| Search member candidates, add member, change role, remove member | ✅ | ✅ | 403 | 403 | 404 |
| List/read tasks | ✅ | ✅ | ✅ | ✅ | 404 |
| Create task / delete task | ✅ | ✅ | 403 | 403 | 404 |
| Edit task title, description, status, priority | ✅ | ✅ | ✅ (own task) | 403 | 404 |
| Assign / unassign users | ✅ | ✅ | 403 | 403 | 404 |
| List comments / add comment | ✅ | ✅ | ✅ | ✅ | 404 |
| Edit or delete a comment | — not offered to anyone — | | | | |

Rules:
- **R1 (Confirmed)** Identity, project owner, task creator, assigner and
  comment author come from the session, never from the request.
- **R2 (Proposed PR1)** A caller who cannot see a project gets `404` for the
  project and everything in it; a member lacking permission gets `403`.
  Administrators never get `403` on project operations. Applies to direct API
  calls with changed identifiers.
- **R3 (Confirmed)** Archived projects are read-only: every write except
  unarchive returns `409 PROJECT_ARCHIVED` (Proposed code). Archiving an
  already archived project is an idempotent no-op (`200`, per the contract).
- **R4 (Proposed PR3)** The owner is always a Manager and cannot be removed or
  demoted; no ownership transfer in release 1.
- **R5 (Proposed PR5)** Removing a member deletes their assignments on that
  project's tasks in the same transaction; their comments stay.
- **R6 (Confirmed)** Eligible assignees = members of the task's project;
  validated in the backend (`422 ASSIGNEE_NOT_ELIGIBLE`), duplicates rejected
  (`409 ALREADY_ASSIGNED`, plus DB primary key).
- **R7 (Proposed PR11)** An Administrator can be assigned only if they are a
  member of the project.

## 5. API operations (contract v0.2.3)

All under `/api/v1`; details, schemas and error codes in
[`openapi.yaml`](../openapi.yaml). Unsafe methods require `X-XSRF-TOKEN`.

| Operation | Method & path | Increment |
|-----------|---------------|-----------|
| Issue CSRF token | `GET /auth/csrf` | Increment 3 |
| Sign in / sign out / current user | `POST /auth/sign-in`, `POST /auth/sign-out`, `GET /auth/me` | Increment 3 |
| List/search, create projects | `GET /projects?search&includeArchived&page&pageSize`, `POST /projects` | Increment 4 |
| Read, edit project | `GET /projects/{id}`, `PATCH /projects/{id}` | Increment 4 |
| Archive / unarchive | `POST /projects/{id}/archive`, `POST /projects/{id}/unarchive` | Increment 4 |
| Members | `GET/POST /projects/{id}/members`, `PATCH/DELETE /projects/{id}/members/{userId}`, `GET /projects/{id}/member-candidates?search` | Increment 4 (API), increments 6/7 (UI) |
| List/filter, create tasks | `GET /projects/{id}/tasks?status&priority&assigneeId&sort&page&pageSize`, `POST /projects/{id}/tasks` | Increment 5 |
| Read, edit, delete task | `GET/PATCH/DELETE /tasks/{id}` | Increment 5 |
| Assign / unassign | `POST /tasks/{id}/assignees`, `DELETE /tasks/{id}/assignees/{userId}` | Increment 6 |
| Comments | `GET/POST /tasks/{id}/comments` | Increment 6 |
| Dashboard | `GET /dashboard` | Increment 7 (backend may land in Increment 5) |

Defaults (Confirmed): pages of 20 (max 100); projects sorted by name,
case-insensitive "contains" search, archived hidden unless
`includeArchived=true`; tasks sorted `-createdAt`. Session idle 30 min,
absolute 8 h; lockout after 5 failures for 15 min; 20 attempts/min/IP → `429`.

## 6. Increments

Each increment lists its components, dependencies, acceptance criteria (AC)
and verification (V). File names are indicative and may be adjusted when the
increment is planned in detail.

### Increment 2 — Application skeleton and persistence

- **Depends on:** Increment 1.
- **Components / files:**
  - `docker-compose.yml` (PostgreSQL 16, named volume, healthcheck),
    `.env.example` (fictitious values: DB name/user/password, ports,
    `MATRIZ_DEMO_PASSWORD`, `MATRIZ_SEED_DEMO=true`), `.gitignore` (`.env`,
    `target/`, `node_modules/`, `dist/`).
  - `backend/`: `pom.xml` + Maven wrapper, `MatrizApplication`,
    `application.yml` (datasource from env vars, Flyway, Spring Session JDBC,
    JPA `ddl-auto=validate`, `open-in-view=false`), module packages
    `identity`, `projects`, `tasks`, `comments`, `dashboard`, `shared`.
  - Flyway: `V1__schema.sql` (users, project, project_member, task,
    task_assignee, comment; constraints and indexes from `database.md`),
    `V2__spring_session.sql`.
  - Demo seed: `DemoDataSeeder` (runs only when `MATRIZ_SEED_DEMO=true` and
    the users table is empty; never deletes data): vitor@matriz.test
    (Administrator), ana@matriz.test (Manager of "Website Redesign"),
    rui@matriz.test (Member there), marta@matriz.test (Manager of a second
    project, outsider to the first), joao@matriz.test (no projects); a few
    tasks, assignments and comments; one shared fictitious password from
    `MATRIZ_DEMO_PASSWORD`, hashed with Argon2id.
  - Tests: `ModularityTests` (Spring Modulith `verify()`), migration test
    with Testcontainers, seed idempotency test.
  - `frontend/`: Vite React TS scaffold, `vite.config.ts` proxy `/api` →
    `http://localhost:8080`, `npm run api:generate` / `api:check`, Vitest
    setup.
  - `README.md`: prerequisites and commands.
- **AC:**
  1. `docker compose up -d db` then `./mvnw spring-boot:run` starts the API;
     Flyway creates the schema.
  2. A health/smoke check proves database access.
  3. Restarting the backend keeps existing data and does not duplicate the
     seed.
  4. No real credentials committed; `.env` ignored.
  5. `npm run dev` serves the SPA shell.
- **V:** `./mvnw verify` (Modulith, migration, seed tests), manual start +
  restart, `docker compose exec db psql -c '\dt'`, `git status` shows no
  `.env`.
- **As built:**
  - Spring Boot 4.1 with Spring Modulith 2.1 and Spring Session JDBC; an
    extra `demo` module holds `DemoDataSeeder` and `DemoProperties`. The
    `identity` module has a deny-by-default `SecurityConfig` (only
    `GET /actuator/health` is open) and the Argon2id `PasswordEncoder`;
    sign-in endpoints come in Increment 3.
  - `spring-boot:run` uses the `local` profile, which imports the root
    `.env`; tests never read `.env` and use Testcontainers (PostgreSQL 16).
  - The demo seed also adds an archived project ("Legacy Intranet", managed
    by marta) so the archived state can be tested later; 5 users,
    3 projects, 4 memberships, 9 tasks, 5 assignments, 4 comments.
  - Backend tests: `ModularityTests`, `MigrationTests` (versions, tables,
    constraints, cascades), `DemoDataSeederTests` (seed, idempotency,
    disabled flag, missing password, Argon2id hashes),
    `ApplicationSmokeTests` (health with `db` UP; API closed), and
    `ContractTests` (loads `openapi.yaml` in the request validator).
  - Contract validator: the library is now published as
    `com.atlassian.oai:openapi-request-validator-*` (3.0.0, formerly
    `swagger-request-validator-*`); it loads the OpenAPI 3.1 contract.
  - Frontend: Vite 8, React 19, TanStack Query, `openapi-fetch` client in
    `src/api/client.ts` with types generated by `openapi-typescript` into
    `src/api/schema.d.ts`; Vitest + React Testing Library smoke test;
    TypeScript pinned to 5.9 because `openapi-typescript` 7 does not yet
    support TypeScript 7. The Vite proxy follows `MATRIZ_API_PORT`.
  - Verified: `./mvnw verify` (18 tests), `npm run build`, `npm test`,
    `npm run lint`, `npm run api:check`; manual start applied V1–V2 and
    seeded the data, health reported `db` UP, a restart logged "seeding
    skipped" with unchanged row counts, `psql \dt` listed the tables, and
    the SPA shell and `/api` proxy answered on port 5173.

### Increment 3 — Authentication and server-side authorization

- **Depends on:** Increment 2.
- **Components / files:** `SecurityConfig` (session fixation protection,
  CSRF cookie-to-header, JSON 401/403 entry points, session cookie
  `matriz_session`, timeouts), `AuthController` (`/auth/csrf`, `/auth/sign-in`,
  `/auth/sign-out`, `/auth/me`), `LoginAttemptService` (account lockout),
  `SignInRateLimiter` (per IP), `CurrentUser` resolver, `ProjectAccessPolicy`
  (visibility → 404, permission → 403), global `ProblemDetail` handler
  (`ProblemCodes`). Frontend: API client module (`openapi-fetch` + CSRF
  middleware + 401 handling), `AuthProvider`, `RequireAuth` route guard,
  `SignInPage`, user menu with sign-out, "Your session has expired" message.
- **AC:**
  1. Valid credentials → `200` + session cookie; invalid → generic `401`.
  2. Requests without a session → `401`; UI redirects to sign-in.
  3. After sign-out the old session cookie is rejected.
  4. Unsafe request without CSRF token → `403 CSRF_INVALID`.
  5. 5 failed attempts lock the account for 15 min; 21st attempt/min/IP →
     `429`.
  6. Only Argon2id hashes stored; no passwords or tokens in logs/errors.
  7. Session lifecycle documented in README and contract.
- **V:** MockMvc + Testcontainers security tests (valid/invalid, no session,
  sign-out invalidation, CSRF, lockout, session ID rotation); Vitest for the
  sign-in form validation; manual sign-in with two demo users.
- **As built:**
  - Backend `identity` module: `SecurityConfig`, `AuthController`,
    `SignInService` (credential check and account lockout; replaces the
    planned `LoginAttemptService`), `SignInRateLimiter`,
    `AbsoluteSessionTimeoutFilter`, `SecurityExceptionHandler`. The session
    principal is `AuthenticatedUser` (id, email, name, admin), injected with
    `@AuthenticationPrincipal` (replaces the planned `CurrentUser`
    resolver); Spring Session stores the user id as the principal name.
  - Shared error layer (`shared` package): `ProblemCode` (the contract's
    codes), `Problems`, `ApiException` and `ApiExceptionHandler`. Every error,
    including 401/403 from the security filters, is `application/problem+json`
    with `type`, `title`, `status`, `code` (and `errors` for `400`); unknown
    errors become a generic `500 INTERNAL_ERROR` without details.
  - Reusable authorization for Increments 4–6 (`projects` module):
    `ProjectAccessPolicy.require(projectId, user, action)` and
    `requireTask(projectId, taskId, user, action)` return a `ProjectAccess`
    (role, admin, archived) or throw; `ProjectAction` lists the actions of
    the §4 matrix. Read through a `JdbcClient` projection until the project
    entities exist (Increment 4).
  - Frontend: `api/client.ts` middleware (adds `X-XSRF-TOKEN` from the
    `XSRF-TOKEN` cookie on unsafe methods, fetching `/auth/csrf` first when
    missing; reports `401`s), `api/auth.ts`, `AuthProvider`, `RequireAuth`,
    `SignInPage` (client-side validation; generic message for invalid
    credentials; throttling message), and a signed-in landing page with the
    user's name and sign-out (the project list arrives in Increment 4).
  - Contract v0.2.1: optional RFC 9457 `instance` member added to `Problem`
    (Spring fills it with the request path); session lifecycle details added
    to the auth operations.
  - Decisions taken while building (within ADR-0009/ADR-0010 and §4):
    - Check order for project resources: visibility (`404`) → permission
      (`403`) → archived state (`409 PROJECT_ARCHIVED`); unarchive is the only
      write allowed on an archived project. A task of another project is `404`.
      Whether a Member is an assignee is read from the database.
    - Sign-in always starts a brand-new session (any previous session is
      invalidated), so the 8 h absolute limit counts from sign-in. Idle
      timeout 30 min (`spring.session.timeout`); the absolute limit is
      checked on each request and the session is deleted when exceeded.
    - Lockout: the 5th consecutive wrong password locks the account for
      15 min and resets the counter; attempts on locked, inactive or unknown
      accounts are not counted and get the same `401 INVALID_CREDENTIALS`
      (with a dummy hash check for unknown emails to even out timing). A
      successful sign-in resets the counter. Locking is logged at WARN with
      the user id only.
    - Rate limit: 20 sign-in attempts per minute per client IP, in memory,
      sliding window; the 21st gets `429 TOO_MANY_ATTEMPTS` with
      `Retry-After`. The IP is the connection's remote address
      (`X-Forwarded-For` is not trusted), so behind the Vite dev proxy all
      browsers share one bucket; a deployment behind a proxy must configure
      forwarded headers explicitly. The Docker Compose stack (Increment 9)
      does: the backend trusts `X-Forwarded-For` from the nginx container
      (`SERVER_FORWARD_HEADERS_STRATEGY: native`), and nginx replaces the
      header with its peer address so a client cannot choose its bucket
      (review finding F-02, `docs/quality-security-review.md`).
    - CSRF: `XSRF-TOKEN` cookie (readable by the SPA, `SameSite=Lax`) and
      `X-XSRF-TOKEN` header on every unsafe method, sign-in and sign-out
      included; the token is rotated at sign-in and the cookie cleared at
      sign-out.
    - Cookies: `matriz_session` is `HttpOnly`, `SameSite=Lax`, path `/`;
      both cookies are `Secure` except in the `local` profile
      (`matriz.auth.secure-cookies=false`, plain HTTP on localhost).
    - The session user is re-read on every request (`CurrentUserFilter`,
      added in Increment 9; originally only `/auth/me`), so a deactivated
      user loses access on the next request; unknown JSON properties in request bodies are rejected
      (`400`).
    - Error codes for other framework errors: `404` → `NOT_FOUND`, `405`,
      `415` and other `4xx` → `VALIDATION_ERROR`.
  - Tests: `AuthenticationTests` (19: CSRF cookie, valid/invalid/unknown/
    locked credentials with identical generic `401`, validation, no session,
    two users on `/auth/me`, session stored with 30 min idle timeout, new
    session at sign-in, sign-out invalidation, missing/wrong CSRF token, idle
    and absolute expiry, deactivated user, lockout and unlock, `429` with
    `Retry-After`, Argon2id-only storage; happy paths validated against
    `openapi.yaml`), `ProjectAccessTests` (34, the §4 matrix action by
    action), `ProjectAccessPolicyTests` (8, over HTTP with real sessions:
    Manager, Member, Assignee, Outsider `404`, Administrator, archived `409`,
    unknown project/task `404`). Frontend Vitest (6): redirect without
    session, form validation, invalid credentials, throttling, sign-in with
    CSRF header and sign-out, session-expired message.
  - Verified: `./mvnw verify` (80 tests), `npm test`, `npm run lint`,
    `npm run build`, `npm run api:check`; manual run (local profile):
    `/auth/me` without session `401`, CSRF `204`, wrong password generic
    `401`, sign-in `200` with `matriz_session` (`HttpOnly; SameSite=Lax`),
    `/auth/me` `200`, sign-out without token `403 CSRF_INVALID`, sign-out
    `204`, old cookie `401`; `spring_session.max_inactive_interval` = 1800;
    no password in the logs.

### Increment 4 — Project management

- **Depends on:** Increment 3.
- **Components / files:** `projects` module (entity, repository with DTO
  projections, `ProjectService`, `ProjectController`, `MemberController`),
  task-count query (shared with dashboard), contract tests. Frontend:
  `ProjectsPage` (list, search, "show archived", pagination, create form),
  `ProjectDetailPage` header (edit, archive/unarchive with confirmation,
  "Archived" badge), basic `MembersPanel`.
- **AC:**
  1. **First increment (Required):** sign in, create a project, see it in the
     list, and read it again after reloading the page and restarting the
     backend.
  2. Name 3–100 and description ≤ 1000, trimmed, whitespace-only rejected
     (`400` with field errors shown in the form).
  3. Owner = caller; list and operations limited to accessible projects;
     outsider gets `404` even with a guessed id.
  4. Case-insensitive name search and pagination per contract; archived hidden
     by default.
  5. Archive asks for confirmation; archived project is read-only (`409`),
     kept with tasks/comments, unarchive restores it.
  6. Managers add/remove members and change roles; owner protected; removal
     deletes the member's assignments.
- **V:** integration tests (create + read back from DB, validation,
  authorization with ana vs marta, archive read-only), contract validation,
  manual first-increment scenario.

- **As built:**
  - Backend `projects` module: `Project` and `ProjectMember` entities,
    `ProjectRepository`, `ProjectMemberRepository`, `ProjectQueries`
    (`JdbcClient` list/read projections with owner, role, member count and
    task counts), `ProjectService`, `ProjectRules` (validation),
    `ProjectCreateRequest` / `ProjectUpdateRequest` (PATCH tracks which
    fields were sent) and a single `ProjectController` for projects, members
    and member candidates (replaces the planned separate `MemberController`).
    Every operation goes through `ProjectAccessPolicy` from Increment 3.
  - Module boundaries (verified by Spring Modulith): `projects` defines the
    `TaskCountsProvider` SPI, implemented by `tasks.TaskStatistics`: one
    `count(*) FILTER (WHERE status = …)` query per page of projects returns
    the counts and % Done (`TaskProgress`), both computed in SQL; the
    dashboard totals use the same SPI. Removing a member publishes `ProjectMemberRemoved`;
    `tasks.AssignmentCleanup` deletes that user's `task_assignee` rows on the
    project's tasks in the same transaction (comments stay). Users are read
    through `identity.UserDirectory`.
  - `dashboard` module: `GET /dashboard` returns the caller, totals per status
    over their active accessible projects, and the first page of those
    projects with per-status counts and `progressPercent`
    (`floor(100 × done / total)`, `0` without tasks), all computed in SQL.
  - Shared: `Text` (trim, blank → null), `Paging` (defaults 1/20, max 100),
    `PageResponse`; `ApiException.validation(...)` produces `400` field
    errors like bean validation does.
  - Contract v0.2.2: owner role change and member-candidate rules made
    explicit (see decisions); generated frontend types refreshed.
  - Frontend: `api/projects.ts`, `AppLayout` (header with the user's name,
    Admin badge and sign-out), `ProjectsPage` (list, name search, "Show
    archived", pagination, create form), `ProjectDetailPage` (facts, edit,
    archive with confirmation, unarchive, "Archived" badge and read-only
    notice), `MembersPanel` (list; Managers change roles and remove members
    with confirmation; the owner row has no controls), `ProjectForm` (client
    validation, server field errors, input kept on failure). The signed-in
    landing page now is the project list (`HomePage` removed). Adding members
    (candidate search) is UI work of Increment 6; the dashboard UI is
    Increment 7.
  - Decisions taken while building (within §4, ADR-0007 and the contract):
    - Create: name and description are trimmed; whitespace-only name or a
      name outside 3–100, or a description over 1000, gives `400` with
      field errors; an empty description is stored as null. The caller
      becomes owner and `MANAGER` member; `201` with `Location`. Projects are
      never deleted (`DELETE` → `405`).
    - PATCH: only sent fields change; `{}` → `400` (field `body`); `name: null`
      → `400`; `description` null or empty clears it; `ownerId`, `status` and
      other unknown properties → `400`. `updated_at` changes on edit, archive
      and unarchive (not on no-op archive/unarchive).
    - Archive is idempotent (`200` unchanged on an archived project), so the
      policy exempts both archive and unarchive from `409 PROJECT_ARCHIVED`.
    - List: accessible projects only (all for Administrators), ordered by
      lower-case name then id; `search` is trimmed, case-insensitive
      substring with `%`/`_` taken literally, max 100 characters; archived
      hidden unless `includeArchived=true`; `page`/`pageSize` out of range →
      `400`. The dashboard lists active projects only, same order.
    - Members: error order `404` (project) → `403` → `409` archived → `404`
      (not a member) → `422`. Adding an unknown or inactive user →
      `422 USER_NOT_ELIGIBLE`; an existing member → `409 ALREADY_MEMBER`
      (checked first and backed by the primary key); default role `MEMBER`;
      `201` without `Location`. Owner: setting `MANAGER` is a no-op `200`;
      demoting to `MEMBER` or removing → `422 OWNER_IMMUTABLE`.
    - Member candidates: Managers and Administrators only; allowed on
      archived projects (a read); `search` trimmed, 2–100 characters,
      matches name or email case-insensitively; active non-members only; at
      most 20, ordered by name.
  - Tests: `ProjectApiTests` (9: create and read back from the database,
    validation, PATCH semantics, outsider `404`, member `403`, list search,
    paging and archived filter, archive/unarchive with read-only `409`, no
    delete), `MemberApiTests` (7: list, candidates, add, duplicates and
    ineligible users, role change, owner protection, removal deletes
    assignments and keeps comments), `DashboardApiTests` (4: totals,
    per-project counts and percentages against the seed, archived excluded,
    Administrator sees all); happy paths validated against `openapi.yaml`.
    `ProjectAccessTests` and `ProjectAccessPolicyTests` cover the archive
    exemption. Frontend Vitest (7 new): list and search, show archived and
    pagination, create with validation and server errors, detail and edit,
    archive confirmation and unarchive, member role change and removal,
    owner row without controls.
  - Verified: `./mvnw verify` (100 tests, Modulith verification), `npm test`
    (13), `npm run lint`, `npm run build`, `npm run api:check`, Redocly lint;
    manual run (local profile): sign in as ana, create a project (`201`,
    name trimmed, owner ana, role `MANAGER`), find it with a
    case-insensitive search and on the dashboard, restart the backend, read
    it again.

### Increment 5 — Task management

- **Depends on:** Increment 4.
- **Components / files:** `tasks` module (entity with `@Version`,
  `TaskService`, `TaskController`, list query with filters/sort/pagination),
  `GET /dashboard` backend if convenient. Frontend: task list with status and
  priority filters, sort and pagination on `ProjectDetailPage`, task create
  form, `TaskDetailPage` (edit title/description/status/priority, delete with
  confirmation), TanStack Query invalidation of task list, project counts and
  dashboard.
- **AC:**
  1. Create, list, read, edit and delete tasks of an accessible project; title
     3–150, description ≤ 2000, whitespace-only rejected.
  2. Status defaults to `TODO`, priority to `MEDIUM`; filters by status and
     priority (multi-select) and pagination work; counts reflect all tasks.
  3. Assignees edit only their own tasks; Members read-only (`403`).
  4. Stale `version` → `409`, UI asks to reload without losing typed input.
  5. Delete removes assignments and comments in one transaction.
  6. New/edited tasks appear without page reload; data persists after backend
     restart.
- **V:** integration tests (validation, permissions, `409`, cascade delete,
  filters), Vitest for the task form, manual scenario with restart.

- **As built:**
  - Backend `tasks` module: `Task` entity (`@Version`, starts at 1),
    `TaskRepository`, `TaskQueries` (`JdbcClient` read projections: one
    count and one page query with the filters, then one batched
    `task_assignee` query per page), `TaskService`, `TaskRules` (PATCH
    validation), `TaskCreateRequest` / `TaskUpdateRequest` (PATCH tracks
    which fields were sent), `TaskSort`, `TaskFilter` and `TaskController`
    (`GET`/`POST /projects/{id}/tasks`, `GET`/`PATCH`/`DELETE /tasks/{id}`).
    Every operation goes through `ProjectAccessPolicy` (`requireTask`
    checks that the task belongs to the project and whether the caller is an
    assignee). `projects` exposes `ProjectMembership` (non-members among a
    set of users) so `tasks` can check assignee eligibility without reaching
    into the projects internals; users are read through
    `identity.UserDirectory`. Contract unchanged (v0.2.2).
  - Decisions taken while building (within §4, ADR-0005, ADR-0013,
    ADR-0014 and the contract):
    - Error order: `400` (body and query) → `404` (project not visible or
      task missing/not visible) → `403` → `409 PROJECT_ARCHIVED` → `409
      VERSION_CONFLICT` (PATCH) or `422 ASSIGNEE_NOT_ELIGIBLE` (create).
    - Create (Managers, Administrators): title trimmed, 3–150, not
      whitespace-only; description trimmed, blank stored as null, ≤ 2000;
      priority defaults to `MEDIUM` (also when sent as null); status is
      always `TODO` (`status` and other unknown properties → `400`).
      `assigneeIds` is optional (≤ 50, no duplicates → `400` on
      `assigneeIds`); each must be an active member of the project, else
      `422 ASSIGNEE_NOT_ELIGIBLE` and nothing is created; the creator is
      recorded as `assigned_by`. `201` with `Location: /api/v1/tasks/{id}`.
      The UI create form has no assignee picker yet (Increment 6).
    - PATCH: `version` required (≥ 1) plus at least one editable field
      (`body` error otherwise); only sent fields change; title null or
      blank → `400`; description null or blank clears it; status or
      priority null or unknown → `400`; any other property → `400`. A
      `version` different from the stored one → `409 VERSION_CONFLICT`; a
      concurrent update caught by JPA `@Version` also gives `409`. Every
      successful PATCH increments `version` and sets `updated_at`, even if
      the values are unchanged. Assignees may change all four editable
      fields of their own tasks; other Members get `403`.
    - Delete (Managers, Administrators): hard delete; the `ON DELETE
      CASCADE` foreign keys remove `task_assignee` and `comment` rows in the
      same statement; `204`, then `404` for the id.
    - List (members, Administrators): filters combine with AND; `status`
      and `priority` take comma-separated (or repeated) values, blank
      entries ignored, unknown values → `400`; `assigneeId` is a user id
      (any id; no membership check, an unknown id lists nothing) or `none`
      for unassigned tasks; `sort` uses semantic enum order, title is
      case-insensitive, ties by `createdAt` descending then id; paging as
      in Increment 4. Assignees are sorted by name. Archived projects stay
      readable.
  - Frontend: `api/tasks.ts` (types, client validation, error messages,
    calls; comma-separated array query parameters as in the contract),
    `TasksPanel` on `ProjectDetailPage` (status and priority checkboxes,
    assignee filter Anyone/Me/Unassigned/each member, sort, pagination,
    inline status select for Managers, Administrators and the task's
    assignees, "New task" for Managers on active projects), `TaskForm`
    (title, description, priority and, when editing, status; client
    validation, server field errors, input kept on failure) and
    `TaskDetailPage` at `/tasks/:taskId` (facts, edit, delete with
    confirmation, back to the project). Editing sends only the fields
    changed since editing started, with the latest version read; on `409`
    the form shows a "Reload task" button that refetches the task and keeps
    the typed input, so saving again applies the user's changes on top of
    the latest version. After every change the task, project (including
    task lists and counts), project list and dashboard queries are
    invalidated.
  - Tests: `TaskApiTests` (9: create with defaults and read back from the
    database, `Location`, validation, assignee eligibility, filters, sorts
    and paging, access matrix with ana/rui/marta/vitor, stale version and
    partial updates, archived project read-only, cascade delete with
    counts); happy paths and error responses validated against
    `openapi.yaml`. The contract validator cannot parse comma-separated
    array query parameters whose items are a `$ref`, so that one request
    check (`validation.request.parameter.schema.invalidJson`) is ignored in
    `ApiIntegrationTest`; responses are still validated. Frontend Vitest
    (10 new): create with client and server validation and count refresh,
    filters/sort/pagination query parameters, inline status change, member
    and archived read-only views, edit with version conflict and reload
    keeping input, edit validation, delete confirmation, assignee may edit
    but not delete, unknown task.
  - Verified: `./mvnw verify` (109 tests, Modulith verification), `npm test`
    (23), `npm run lint`, `npm run build`, `npm run api:check`; manual run
    (local profile): sign in as ana, create a task in Website Redesign
    (`201`, title trimmed, `TODO`/`HIGH`, version 1), filter it by status
    and priority, restart the backend, read it again (unchanged), update it
    (`200`), delete it (`204`).

### Increment 6 — Assignees and comments

- **Depends on:** Increment 5.
- **Components / files:** assignment endpoints in `tasks`, `comments` module
  (`CommentService`, `CommentController`). Frontend: assignee selector fed by
  `GET /projects/{id}/members`, assignee chips with remove, comments list
  (author, date) and form on `TaskDetailPage`; member-candidate search in
  `MembersPanel`.
- **AC:**
  1. Several members can be assigned to one task and each assignment removed.
  2. Duplicate assignment → `409`; non-member/unknown user → `422`; no
     duplicate rows.
  3. Comments 1–2000, whitespace-only rejected; author and date set by the
     backend and displayed.
  4. Assignments and comments persist after reopening the task and restarting
     the backend.
  5. No comments on archived projects.
- **V:** integration tests (duplicate, eligibility, author inferred even if a
  client sends another id, persistence), Vitest for comment form, manual check.

- **As built:**
  - Backend: `TaskService.assign` / `unassign` and two endpoints in
    `TaskController` (`POST /tasks/{id}/assignees` → `201` with the task,
    `DELETE /tasks/{id}/assignees/{userId}` → `204`). New `comments` module:
    `Comment` entity, `CommentRepository`, `CommentQueries` (`JdbcClient`
    page query, authors read in one batch through
    `identity.UserDirectory`), `CommentService`, `CommentCreateRequest`,
    `CommentView` and `CommentController` (`GET`/`POST
    /tasks/{id}/comments`). `tasks` exposes `TaskLookup` (the project of a
    task) so `comments` can authorize through `ProjectAccessPolicy` without
    reaching into the tasks internals; `tasks` does not depend on
    `comments`. Contract unchanged (v0.2.2).
  - Decisions taken while building (within §4, R6, R7, BR15, ADR-0013 and
    the contract):
    - Assign/unassign (Managers, Administrators): error order `400` →
      `404` (task missing or project not visible) → `403` → `409
      PROJECT_ARCHIVED` → `409 ALREADY_ASSIGNED` → `422
      ASSIGNEE_NOT_ELIGIBLE`; a duplicate is reported before eligibility,
      as `ALREADY_MEMBER` is for members. Eligible = active user who is a
      member of the task's project (an Administrator who is not a member is
      not eligible). The primary key on `(task_id, user_id)` plus `ON
      CONFLICT DO NOTHING` guarantees no duplicate rows under concurrent
      requests (also `409`). The caller is recorded as `assigned_by`.
      Removing a user who is not assigned → `404`. Assignment changes do
      not change the task `version` or `updatedAt`, so they never cause a
      `409 VERSION_CONFLICT` for someone editing the task.
    - Comments: any member of the project and Administrators may list
      (also on archived projects) and add (not on archived projects, `409
      PROJECT_ARCHIVED`); outsiders get `404`. Body trimmed, then 1–2000
      characters (whitespace-only and blank → `400` on `body`); the limit
      applies after trimming. Author and date come from the session and the
      server clock (truncated to microseconds, the database precision); any
      other property, e.g. `authorId` or `createdAt`, → `400`. `201` with
      the comment and no `Location` header (comments have no own URL).
      Listing is oldest first (`createdAt`, then id), paged like other
      lists (default 20, max 100). No edit or delete; comments go with
      their task (cascade).
  - Frontend: `api/comments.ts` (types, client validation, messages,
    calls); `api/tasks.ts` (`assignTask`, `unassignTask`, assignees on
    create) and `api/projects.ts` (`searchMemberCandidates`, `addMember`).
    `TaskForm` has an assignee checkbox picker on create (fed by `GET
    /projects/{id}/members`). `TaskDetailPage` shows `AssigneesPanel`
    (assignee list; for Managers and Administrators on active projects a
    "Remove …" button per assignee and an "Add assignee" select offering
    only members not yet assigned) and `CommentsPanel` (author and local
    date and time, oldest first, paginated; add form with client
    validation, input kept on errors, controls disabled while posting,
    jumps to the last page after posting; hidden on archived projects,
    where the read-only notice is shown). `MembersPanel` adds members:
    candidate search (2–100 characters), role select, "Add …" per result.
    Task, project, project list and dashboard queries are refreshed after
    each change.
  - Tests: `AssigneeApiTests` (7: several assignees removed one by one and
    persisted, duplicate, eligibility of non-member, Administrator
    non-member, unknown and inactive users, validation, access matrix,
    archived project, member removal) and `CommentApiTests` (7: author and
    date from the server and persisted, client-supplied author or date
    rejected, length and whitespace validation after trimming, order and
    paging, access matrix, archived project, cascade on task delete);
    responses validated against `openapi.yaml`. `DashboardApiTests` now
    asks for a larger page, as other tests add projects managed by Ana.
    Frontend Vitest (7 new): assignees on create, add and remove assignees
    on the task page, read-only assignees, comment validation, posting and
    listing with author and date, input kept on a server error, comments
    read-only on archived projects, member candidate search and add.
  - Verified: `./mvnw verify` (123 tests, Modulith verification), `npm test`
    (30), `npm run lint`, `npm run build`, `npm run api:check`; manual run
    (local profile): sign in as ana, create a task in Website Redesign,
    assign Rui (`201`), assign him again (`409 ALREADY_ASSIGNED`), assign
    an unknown user (`422`), comment with surrounding spaces (`201`,
    trimmed, author Ana), blank comment (`400`); restart the backend; the
    task still has Rui as assignee (version 1) and the comment is listed;
    unassign (`204`) and delete the task (`204`).

### Increment 7 — Frontend completion

- **Depends on:** increments 3–6.
- **Components / files:** `DashboardPage` (user, accessible active projects,
  counts per status, % Done, links), shared layout/navigation, reusable
  `FormField`, `ConfirmDialog`, `StatusBadge`, `PriorityBadge`,
  `EmptyState`, `ErrorMessage`, `Pagination`; responsive CSS; single API
  base-URL configuration (`/api/v1`, overridable via `VITE_API_BASE_URL`).
- **AC (Required):**
  1. Screens: sign-in, dashboard, projects, project detail, task detail —
     coherent with the wireframe (deferred features omitted).
  2. Loading, empty, success and error states on every screen; form data kept
     when a submission fails.
  3. Submit buttons disabled while a request is in flight; UI updated from the
     API response without duplicates.
  4. Lists, filters and counts synchronized after create/edit/delete; totals
     reflect the whole data set.
  5. Layout adapts to narrow screens; no mocks left for completed features.
- **V:** Vitest + React Testing Library on key components; manual end-to-end
  walkthrough with API and DB; check at mobile and desktop widths.
- **As built:**
  - Routes (all but sign-in behind the session guard): `/` dashboard (the
    landing page after sign-in), `/projects`, `/projects/{id}`,
    `/tasks/{id}`, `/sign-in`; any other address shows a "Page not found"
    page with links back. Each page sets the document title ("… · Matriz").
  - Layout (`AppLayout`): header with the product name, main navigation
    (Dashboard, Projects; current page marked), the signed-in user (with an
    Administrator badge) and Sign out; a "Skip to main content" link; one
    polite status region for success messages ("Task “…” created.",
    "Task saved.", "Project archived.", "… removed from the project.", …),
    dismissible and cleared after 6 seconds. Breadcrumbs on the project and
    task pages (Projects › project › task).
  - `DashboardPage` (`GET /dashboard`): "Signed in as …", totals for the
    tasks of the user's active projects (progress bar, % Done, counts per
    status), then the active projects (name linking to the project, the
    user's role, member count, compact progress), paginated; empty state
    linking to the projects page. Archived projects are not listed (as the
    endpoint defines).
  - Shared components: `FormField` (label, hint and error wired with
    `aria-describedby`/`aria-invalid`), `ConfirmDialog` (modal
    `alertdialog`: focus starts on Cancel, Tab stays inside, Escape
    cancels, focus returns to the trigger, errors shown inside, buttons
    disabled while pending), `StatusBadge`, `PriorityBadge`,
    `ProjectStatusBadge`, `EmptyState`, `ErrorMessage` (role `alert`,
    optional "Try again" or other action), `Loading`, `Pagination` ("Page X
    of Y (N items)", hidden for a single page), `Breadcrumbs`,
    `TaskSummary`.
  - Confirmations: archive project, delete task and remove member use
    `ConfirmDialog` and say what is lost (read-only project; assignments
    and comments; task assignments). Unarchive and unassign are immediate
    (reversible).
  - Role-aware UI unchanged in substance and applied everywhere: actions
    the user may not perform are not shown; archived projects show the
    Archived badge, a read-only notice and no edit controls. The backend
    remains the authority (`403`/`409` messages if the UI is stale).
  - Errors: `api/errors.ts` maps every failure to a user message — network
    failure ("Cannot reach the server…"), `PROJECT_ARCHIVED`,
    `CSRF_INVALID`, `400` (first field message), `401` (session expired:
    the auth layer returns to sign-in with a notice and the original
    address), `403`, `404`, `409`, `422`, `429`, `5xx`; feature modules map
    their own codes first (e.g. `VERSION_CONFLICT` with "Reload task").
    Queries are not retried on `4xx`; network errors and `5xx` are retried
    twice, then shown with "Try again".
  - Synchronization: after each change the affected task, project, project
    list and dashboard queries are refreshed (`invalidateProjectViews`,
    `invalidateTaskViews`). Polling every 60 seconds on the dashboard,
    project list, project, task list, task and comments (TanStack Query;
    paused in hidden tabs). No real-time channel (out of scope). The task
    edit form keeps the version read when editing started (or the one the
    user reloaded), so a background refresh never hides a `409
    VERSION_CONFLICT`.
  - Task list: a table (Task, Assignees, Status, Priority) with the
    existing filters, sort and pagination plus "Clear filters"; inline
    status change for users who may edit the task; on narrow screens each
    row becomes a stacked card with cell labels.
  - Forms mirror the API rules (lengths, required fields, comment limit)
    with visible hints; input kept on errors; submit disabled while
    pending.
  - Deactivated members (gap found while auditing the assignee picker,
    which offered them and got `422`): `ProjectMember` gains a required
    `active` flag (contract v0.2.3, additive). The members list shows a
    "Deactivated" badge; the create-task picker and "Add assignee" offer
    active members only; the task filter still lists them (marked
    "(deactivated)") so their existing tasks remain findable.
    `UserDirectory.activeIds` reads the flag in one query.
  - Responsive CSS (≤ 640 px): navigation wraps under the brand, task table
    rows stack, summary cards shrink; visible focus outline everywhere.
  - Tests: `MemberApiTests.listFlagsDeactivatedMembers` (backend); Vitest
    `ui.test.tsx` (10 new: dashboard counts and % done, empty state, server
    error with retry, network error, main navigation and sign-out,
    not-found page, dialog focus/Escape, member removal with confirmation
    message, deactivated members excluded from pickers, success message
    after creating a task); existing tests updated for the new routes and
    markup.
  - Verified: see the change log entry for the checks run.

### Increment 8 — MVP verification

- **Depends on:** Increment 7.
- **Components / files:** `docs/verification.md` (verification cases 01–09
  with expected result, observed result, evidence; commands; limitations;
  manual vs automated), regression tests for bugs found. Cases:
  | # | Scenario | Result to confirm |
  |---|----------|-------------------|
  | 01 | Access | Valid credentials sign in; invalid credentials and unauthenticated requests are handled. |
  | 02 | Projects | Create, search, edit and archive/unarchive behave according to permissions. |
  | 03 | Task persistence | A new task appears without reload and survives a backend restart. |
  | 04 | Validation | Empty, whitespace-only and invalid values are rejected. |
  | 05 | Status and priority | Editing updates the task, filters and counts consistently. |
  | 06 | Assignees | Assign and unassign work; the same assignment is never duplicated. |
  | 07 | Comments | Content, authenticated author and date are stored and shown. |
  | 08 | API authorization | Another user cannot read or change resources without permission, even with changed identifiers. |
  | 09 | Deletion and errors | Deletion respects dependents; API errors show no success message and keep form data. |
- **AC:**
  1. All nine cases recorded with evidence.
  2. At least three automated tests: a validation rule, an authorization
     scenario, and a create followed by a persistence read-back.
  3. Every bug found has reproduction, cause, limited fix and regression test.
- **V:** `./mvnw verify`, `npm test`, manual runs recorded in
  `docs/verification.md`.
- **As built:**
  - [`verification.md`](verification.md) records cases 01–09 with
    expected result, observed result and evidence (automated tests and a
    manual API and browser run, including a backend restart), the commands
    and their results, and the limitations (no browser E2E suite, restart
    checked manually, k6 deferred).
  - New tests: `ApiAuthorizationTests` (outsider gets `404` on every
    operation of another project and nothing changes; Member gets `403` on
    Manager actions; owner, author, project and assignee cannot be chosen
    by the client), `DashboardApiTests.editsOfStatusAndPriorityKeepTaskFiltersAndCountsConsistent`,
    and two Vitest tests in `tasks.test.tsx` (the edit form keeps the
    version read when editing started across background refreshes —
    regression test for the Increment 7 synchronization fix; a failed save
    shows no success message and keeps the input). `fakeApi` can fail the
    next task update.
  - Minimum tests: validation `TaskApiTests.titleDescriptionAndPriorityAreValidated`;
    authorization `ApiAuthorizationTests.outsiderGetsNotFoundForEveryOperationAndChangesNothing`;
    create + read-back `TaskApiTests.createdTaskHasDefaultsAndIsPersisted`.
  - No bugs found; no product code changed.
  - Verified: `./mvnw verify` (128 tests), `npm test` (42), `npm run lint`,
    `npm run build`, `npm run api:check`, Redocly lint; manual run on
    2026-10-29.

### Increment 9 — Review and delivery

- **Depends on:** Increment 8.
- **Components / files:** diff review notes, optional separate refactor
  commit, updated `openapi.yaml` and affected docs, `AGENTS.md` (run and
  verification commands), `README.md` (requirements, installation,
  configuration, schema, seed, demo accounts, start, tests), three AI-usage
  examples in `docs/verification.md`.
- **AC (Required):** app starts following the README from a clean clone; contract
  matches implementation; docs coherent; work versioned with clear commits.
- **V:** fresh clone run-through, Redocly lint, API type drift check, full
  test suites.
- **As built:**
  - Review of the full release 1 diff (AI-assisted, checked by hand) for
    permissions, validation, error handling, duplication, naming, dead code
    and out-of-scope changes; findings and fixes in
    [`verification.md`](verification.md) §7:
    - B1: a deactivated user kept access until the session expired →
      `identity/CurrentUserFilter` re-reads the session user on every
      request (deactivated or deleted → session invalidated, `401`; name or
      Administrator role changes apply at once). **Proposed:** per-request
      re-check (one primary-key read) rather than a shorter session.
    - B2: emoji passed `@Size` (UTF-16 units) and failed the database
      `char_length` check with `500` → all length limits count Unicode code
      points (`@CodePointLength`, `shared/Text.length()`, frontend
      `api/text.ts`). **Proposed:** match PostgreSQL and JSON Schema
      `maxLength` instead of mapping the constraint violation.
  - Refactor in a separate commit, behaviour preserved (same 132 tests pass):
    `shared/Timestamps.now(clock)` replaces three copies of the
    microsecond-truncated clock read; unused `ProjectAccess.can` removed.
    Other candidates (row mappers in `TaskStatistics`, `instant()` helpers,
    the frontend "can edit task" rule) left unchanged as low value.
  - Contract unchanged (v0.2.3): it already specified `401` for deactivated
    users, and JSON Schema lengths count code points.
  - Docs: README completed (requirements, quick start from a clean clone,
    configuration, schema, seed, demo accounts, run, tests, layout, known
    limitations); `AGENTS.md` run/verify commands and code conventions;
    ADR-0016 accepted; architecture and ADR wording aligned with the
    implementation; three AI-usage examples in `verification.md` §8.
  - Verified: `./mvnw verify` (132 tests), `npm test` (43), `npm run lint`,
    `npm run build`, `npm run api:check`, Redocly lint; a fresh clone
    followed the README (Quick start) and passed the same checks.

## 7. Risks and mitigations

| Risk | Mitigation |
|------|-----------|
| `swagger-request-validator` lacks full OpenAPI 3.1 support | Checked in Increment 2: `openapi-request-validator` 3.0.0 (renamed artifact) loads the 3.1 contract; fallback to JSON Schema validation of component schemas (ADR-0016) kept if gaps appear. Gap found in Increment 5: comma-separated array query parameters with `$ref` items are not parsed; that request check is ignored and the values are covered by `TaskApiTests`. |
| N+1 queries when loading assignees and counts | DTO projections, one batched assignee query per page, one `GROUP BY` for counts. |
| Session cookie `Secure` flag breaks local HTTP | `Secure` only outside the local profile (documented). |
| Testcontainers needs Docker running | Documented prerequisite; Docker 28 available locally. |
| Scope creep into deferred features | Deferred items listed in §2; out of scope unless the user asks. |

## 8. Change log

| Date | Increment | Change |
|------|-----------|--------|
| 2026-10-29 | Increment 1 | Plan created; scope reconciled; access rules consolidated; `openapi.yaml` v0.2.0 completed; ADR-0013–0016 added; ADR statuses reviewed. |
| 2026-10-29 | Increment 2 | Done: backend and frontend skeleton, Docker Compose PostgreSQL, `.env.example`, Flyway V1–V2, idempotent demo seed, generated API client, tests; README getting-started commands. |
| 2026-10-29 | Increment 3 | Done: session authentication (`/auth/*`), CSRF, lockout and rate limit, problem-details error layer, reusable project access policy, frontend sign-in flow, tests; contract v0.2.1 (`Problem.instance`, lifecycle details); README session section; ADR-0016 validator artifact name corrected. |
| 2026-10-29 | Increment 4 | Done: project CRUD with search, paging and archive/unarchive, members and member candidates, `GET /dashboard`, task-count SPI and member-removal event, projects UI (list, detail, members), tests; contract v0.2.2 (owner role change, member-candidate rules); R3 notes idempotent archive. |
| 2026-10-29 | Increment 5 | Done: task create, list (status/priority/assignee filters, sort, paging), read, partial update with optimistic concurrency and hard delete; `ProjectMembership` for assignee eligibility; task list, create form and task detail UI with version-conflict reload; tests; contract unchanged. |
| 2026-10-29 | Increment 6 | Done: assign/unassign endpoints, `comments` module (list, add), `TaskLookup` for cross-module authorization; assignee picker, assignee and comment panels on the task page, member-candidate search and add in the members panel; tests; contract unchanged. |
| 2026-10-29 | Increment 7 | Done: dashboard as landing page, layout with navigation and sign-out, shared form/feedback/dialog/pagination/badge components, confirmations for archive, task delete and member removal, success messages, centralized error messages, 60 s polling, task table with responsive layout, not-found page; contract v0.2.3 (`ProjectMember.active`), deactivated members excluded from assignee pickers; tests. |
| 2026-10-29 | Increment 8 | Done: `docs/verification.md` (cases 01–09 with evidence, commands, results, limitations); authorization, status/priority consistency, refetch-version regression and failed-save tests; manual API, restart and browser run; no bugs found. |
| 2026-10-29 | Increment 9 | Done: diff review; deactivated users lose the session on the next request (`CurrentUserFilter`); length limits count code points; refactor (`Timestamps`, unused `ProjectAccess.can` removed); README, `AGENTS.md`, verification (review findings, AI usage) and ADR/architecture docs completed; ADR-0016 accepted; clean-clone check. Contract unchanged (v0.2.3). |
| 2026-11-12 | Test plan | `docs/test-plan.md`: scenario matrix for task management and permissions, selected scenarios (invalid title, create and read, access without permission) with expected results and associated tests, review of AI suggestions; planned unit, integration and E2E tests and the security review. No product code changed. |
| 2026-11-12 | Test plan | Unit tests of the task title rule (`TaskTitleRuleTests`, `src/api/tasks.test.ts`): valid, invalid and boundary values (2/3, 150/151, trimmed, code points); results and review of the AI-drafted tests in `docs/test-plan.md` §9; finding F-01 (non-breaking-space title accepted by the API) recorded, not fixed; README single-test commands. No product code changed. |
| 2026-11-12 | Test plan | Integration test of the task create and read flow (`TaskCreateReadIntegrationTests`, MockMvc + Testcontainers PostgreSQL, contract-checked): create → read by `Location` → row in the database → project list (CR-01, CR-02), trimmed minimum title (TV-04), invalid title and nonexistent task/project (CR-03), anonymous `401` on task endpoints (AU-01), Member/outsider (AU-02, AU-03); per-test data with cleanup checked after each test; results in `docs/test-plan.md` §10; README command. No product code changed; no new defect found. |
| 2026-11-12 | Test plan | End-to-end test of creating a task through the UI (`frontend/e2e/create-task.e2e.ts`, Playwright + Chromium, `npm run test:e2e`): sign in → open project → create task → task in the list without reload and after a reload (CR-04, CR-02); runs against the Compose images as a separate project with a throw-away seeded database (`frontend/e2e/docker-compose.e2e.yml`); role/label selectors, unique title per run, no fixed waits, trace and screenshot only on failure; results in `docs/test-plan.md` §11; README command and prerequisites; verification limitation updated. No product code changed; no new defect found. |
| 2026-11-12 | Test plan | Code review and security review (`docs/quality-security-review.md`): scope Increment 9 and the Ficha 4 test changes; manual IDOR check with the demo accounts on an isolated Compose stack (no access-control finding); oxlint; Semgrep added (`.semgrep.yml`, `.semgrepignore`, `scripts/semgrep.sh`, README and `AGENTS.md` command) and its findings triaged; secrets scan of the tree and history. Findings: F-01 confirmed for task, project and comment text; F-02 sign-in rate limit bypassed with a forged `X-Forwarded-For` in the Compose stack (contradicts the Increment 3 note); F-03 search lengths in UTF-16 units. No product code changed; fixes pending human decision. |
| 2026-11-12 | Test plan | Review fix F-01 (approved by the project owner): `Text.trim` also removes Unicode space separators (U+00A0, U+2007, U+202F, …) and U+FEFF, so whitespace-only task titles/descriptions, project names/descriptions and comment bodies are rejected or treated as empty as the contract and the UI require; TV-07 tests re-enabled; regression tests `TextTests` and API tests for project name, task title (create and edit) and comment body. |
| 2026-11-12 | Test plan | Review fix F-02 (approved by the project owner): `frontend/nginx.conf` replaces `X-Forwarded-For` with the peer address instead of appending the client's value, so the sign-in rate limit cannot be bypassed in the Compose stack; Increment 3 note and Compose comment corrected; regression Semgrep rule `matriz-nginx-forwarded-for-appends-client-value` and live re-check. F-03 (search lengths) and S-01 (nginx security headers) deferred by the project owner. |
| 2026-11-12 | Test plan | Independent validation of the test and review work (all exercises and completion criteria passed; backend, frontend and E2E results reproduced). Documentation completed: delivery reference, tests not executed and pending problems (`test-plan.md` §12, review §8); outdated "F-01 open" notes marked as written before the fix. |
