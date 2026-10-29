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
| 4 | Project management (incl. archive/unarchive, members) | Planned |
| 5 | Task management | Planned |
| 6 | Assignees and comments | Planned |
| 7 | Frontend completion (dashboard, layout, states, sync) | Planned |
| 8 | MVP verification (`docs/verification.md`, tests) | Planned |
| 9 | Review and delivery | Planned |

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

Proposed technical details ([ADR-0016](adr/0016-contract-first-api-and-generated-client.md)):
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
  unarchive returns `409 PROJECT_ARCHIVED` (Proposed code).
- **R4 (Proposed PR3)** The owner is always a Manager and cannot be removed or
  demoted; no ownership transfer in release 1.
- **R5 (Proposed PR5)** Removing a member deletes their assignments on that
  project's tasks in the same transaction; their comments stay.
- **R6 (Confirmed)** Eligible assignees = members of the task's project;
  validated in the backend (`422 ASSIGNEE_NOT_ELIGIBLE`), duplicates rejected
  (`409 ALREADY_ASSIGNED`, plus DB primary key).
- **R7 (Proposed PR11)** An Administrator can be assigned only if they are a
  member of the project.

## 5. API operations (contract v0.2.1)

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
      forwarded headers explicitly.
    - CSRF: `XSRF-TOKEN` cookie (readable by the SPA, `SameSite=Lax`) and
      `X-XSRF-TOKEN` header on every unsafe method, sign-in and sign-out
      included; the token is rotated at sign-in and the cookie cleared at
      sign-out.
    - Cookies: `matriz_session` is `HttpOnly`, `SameSite=Lax`, path `/`;
      both cookies are `Secure` except in the `local` profile
      (`matriz.auth.secure-cookies=false`, plain HTTP on localhost).
    - `/auth/me` re-reads the user, so a deactivated user loses access on the
      next request; unknown JSON properties in request bodies are rejected
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

## 7. Risks and mitigations

| Risk | Mitigation |
|------|-----------|
| `swagger-request-validator` lacks full OpenAPI 3.1 support | Checked in Increment 2: `openapi-request-validator` 3.0.0 (renamed artifact) loads the 3.1 contract; fallback to JSON Schema validation of component schemas (ADR-0016) kept if gaps appear. |
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
