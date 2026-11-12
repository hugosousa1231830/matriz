# Matriz — Test plan (task management and permissions)

Test plan for one feature already implemented in release 1: **task
management and its permissions**. Scenarios are derived from the
requirements and acceptance criteria, not from the current behaviour of the
code; existing tests are referenced where they already check a scenario.

Sources: [`requirements.md`](requirements.md) (FR3, FR4, security and
privacy NFRs, validation limits), [`user-stories.md`](user-stories.md) (US3,
US4), [`constraints.md`](constraints.md),
[`implementation-plan.md`](implementation-plan.md) (§4 access rules,
Increment 5), [ADR-0010](adr/0010-uniform-error-format-problem-json.md),
[ADR-0013](adr/0013-multiple-assignees-per-task.md) and
[`openapi.yaml`](../openapi.yaml) (`TaskCreateRequest`, `Problem`).

## 1. Scope

- **In:** task title validation, task creation and reading (API, list and
  UI), and access to tasks by users without permission (anonymous,
  outsider, Member).
- **Out:** deadlines and past-deadline confirmation (deferred, FR5 and
  [ADR-0011](adr/0011-past-deadline-confirmation-enforced-by-api.md)), the
  Viewer role (out of scope), status history (deferred, FR10), load tests
  (k6 deferred).

## 2. Test environment and data

- **Isolation:** tests never use the development database (volume
  `matriz-db-data`). Backend tests start their own PostgreSQL 16 container
  with Testcontainers (`TestcontainersConfiguration`), apply the Flyway
  migrations and load the demo seed; frontend tests use the in-memory fake
  API (`src/test/fakeApi.ts`). The E2E run (Ex4 below) uses its own
  database, created for the run and discarded afterwards.
- **Demo accounts** (seed, see README "Demo data"): `ana@matriz.test`
  (Manager of *Website Redesign*), `rui@matriz.test` (Member of *Website
  Redesign*), `marta@matriz.test` (Manager of *Mobile App Launch*,
  outsider to *Website Redesign*), `vitor@matriz.test` (Administrator).
  The password comes from `MATRIZ_DEMO_PASSWORD`; fictitious `.test`
  credentials only, never real secrets.
- **Run:** `cd backend && ./mvnw verify`, `cd frontend && npm test` (see
  [`verification.md`](verification.md) §1).

## 3. Test levels

| Level | Tool | Used for |
|-------|------|----------|
| Unit (U) | JUnit 5 (backend), Vitest (frontend pure functions) | Business rules in isolation (title validation, trimming, boundaries) |
| Component (C) | Vitest + React Testing Library, fake API | Form validation messages, controls shown per role |
| Integration (I) | MockMvc + Testcontainers PostgreSQL, responses checked against `openapi.yaml` (`matchesContract()`) | API behaviour, persistence, authorization and error codes |
| End-to-end (E) | Playwright (Chromium) against the running stack | Main user flow through the browser |
| Review (R) | Manual review, oxlint, Semgrep | Code and security review (IDOR with two demo accounts) |

## 4. Scenario matrix

★ = essential scenario selected for this plan (section 5). Status:
**Covered** (an existing test checks it), **Gap** (not checked yet),
**Planned (ExN)** (test to be added in that exercise).

| ID | Scenario | Input | Expected result | Level | Status |
|----|----------|-------|-----------------|-------|--------|
| TV-01 ★ | Title missing or empty | `{}`, `""` | `400 VALIDATION_ERROR`, field `title`; nothing stored; UI shows "Title is required." | U, I, C | Covered (U, I, C) |
| TV-02 ★ | Title whitespace-only | `"   "`, `"\t\n "` | `400`, field `title`; nothing stored | U, I, C | Covered (U, I, C) |
| TV-03 ★ | Title below minimum after trim | `" ab "` (2) | `400`, field `title` | U, I, C | Covered (U, I, C) |
| TV-04 ★ | Title at minimum | `"abc"`, `"  abc  "` | Accepted; stored as `"abc"` | U, I | Covered (U); Gap (I) |
| TV-05 ★ | Title at maximum and above | 150 / 151 characters | 150 accepted; 151 → `400` | U, I | Covered (U, I) |
| TV-06 | Length counted in characters (code points) | 2 / 3 emoji | 2 → `400`; 3 accepted | U, I | Covered (U, I) |
| TV-07 | Title of non-breaking spaces only | `"\u00A0\u00A0\u00A0"` | `400`, field `title` (whitespace-only; contract `pattern: '\S'`) | U | UI covered (U); API fails — finding F-01 (§9) |
| CR-01 ★ | Manager creates a task and reads it | Ana, title only, *Website Redesign* | `201` with `Location`; `TODO`, `MEDIUM`, version 1, `createdBy` = signed-in user; `GET /tasks/{id}` returns the same task; row in PostgreSQL | I | Covered; Planned (Ex3) |
| CR-02 ★ | Created task appears in the project list | `GET /projects/{id}/tasks` | Task listed; project counts `todo` +1 | I, C, E | Covered (I, C); Planned (Ex3, Ex4) |
| CR-03 ★ | Invalid create is not persisted | Blank title | `400`; task count and list unchanged | I | Covered; Planned (Ex3) |
| CR-04 ★ | Create through the UI | Sign in → open project → create → list | New task visible in the list without reload | E | Gap; Planned (Ex4) |
| AU-01 ★ | Anonymous access to tasks | No session: `GET`/`POST` task endpoints | `401 UNAUTHENTICATED`; nothing created | I | Gap for task endpoints; Planned (Ex3) |
| AU-02 ★ | Outsider uses real identifiers (IDOR) | Marta on a *Website Redesign* project/task: read, list, create, edit, delete | `404 NOT_FOUND` (not `403`); state unchanged; project absent from search and dashboard | I, R | Covered (I); Planned (Ex5, R) |
| AU-03 ★ | Member creates a task | Rui, `POST /projects/{id}/tasks` | `403 FORBIDDEN`; nothing created; UI hides "New task" | I, C | Covered |
| AU-04 | Member edits a task not assigned to them; assignee edits own | Rui | `403` / `200` | I, C | Covered |
| AU-05 | Member deletes a task | Rui | `403` | I | Covered |
| AU-06 | Administrator manages any project | Vitor | Create, edit, delete allowed | I | Covered |
| AU-07 | Client supplies identity or status | `createdById`, `status`, `projectId` | `400`; values never taken from the client | I | Covered |
| AU-08 | Write on an archived project | Create/edit/delete | `409 PROJECT_ARCHIVED`; reads allowed | I | Covered |
| ST-01 | Stale version on edit | `PATCH` with an old `version` | `409 VERSION_CONFLICT` | I, C | Covered |

## 5. Selected essential scenarios

Selected because they are the minimum required for this plan: an invalid
title (US3 required-field criterion and the validation limits), creating
and reading a task (US3 and FR3), and access by a user without permission
(security and privacy NFRs, §4).

### 5.1 Invalid title (TV-01 to TV-05)

- **Rule:** title trimmed, 3–150 characters (code points), not
  whitespace-only; enforced by the API, mirrored in the UI
  (`requirements.md` validation limits; `TaskCreateRequest` in
  `openapi.yaml`).
- **Precondition:** Ana signed in, active project where she is Manager.
- **Expected:** invalid values → `400`, `application/problem+json`, `code`
  `VALIDATION_ERROR`, `errors[0].field` = `title`, no task stored; valid
  boundaries (3 after trim, 150) → `201` with the trimmed title. In the UI
  the form shows the message without calling the API.
- **Associated tests:**
  - Existing (I): `TaskApiTests.titleDescriptionAndPriorityAreValidated`
    (empty, whitespace-only, `" ab "`, 151, missing; 150 accepted; task
    count 0 after the invalid requests),
    `TaskApiTests.lengthsCountCharactersNotUtf16Units`,
    `TaskApiTests.staleVersionIsRejectedAndPartialUpdatesApplyOnlySentFields`
    (blank title on edit).
  - Existing (C): `tasks.test.tsx` "creates a task after validating the
    form, and refreshes the list and counts" (`"   "` → "Title is
    required.", `"ab"` → length message, no `POST`).
  - Unit (U, §9): `TaskTitleRuleTests` (backend: `TaskCreateRequest` with
    Bean Validation, `TaskRules.validate` for edits) and
    `src/api/tasks.test.ts` (frontend `validateTask`) — valid, invalid and
    boundary values (2/3 and 150/151 after trimming, code points), including
    the former gap TV-04 and TV-07.

### 5.2 Create and read a task (CR-01 to CR-04)

- **Precondition:** Ana signed in; project where she is Manager.
- **Input:** `POST /api/v1/projects/{id}/tasks` with `{"title": "…"}`.
- **Expected:** `201`, `Location: /api/v1/tasks/{id}`, status `TODO`,
  priority `MEDIUM`, no assignees, version 1, `createdBy` = Ana (from the
  session); `GET /api/v1/tasks/{id}` returns the same values; the row exists
  in PostgreSQL; the task is in `GET /projects/{id}/tasks`. Negative: an
  invalid title returns `400` and stores nothing.
- **Associated tests:**
  - Existing (I): `TaskApiTests.createdTaskHasDefaultsAndIsPersisted`,
    `TaskApiTests.createReturnsLocationOfTheNewTask`; (C) `tasks.test.tsx`
    create test above.
  - **Planned (Ex3):** integration test create + read through the API with
    a real database (Testcontainers), plus a negative case (CR-03).
  - **Planned (Ex4):** Playwright (Chromium) E2E — sign in → open project →
    create task → task visible in the list (CR-04), against an isolated
    test database.

### 5.3 Access by a user without permission (AU-01 to AU-03)

- **Rule:** a caller who cannot see the project gets `404` for it and
  everything in it; a member without the permission gets `403`; no session
  gets `401` (`implementation-plan.md` §4 R1/R2; ADR-0010).
- **Expected:** anonymous → `401 UNAUTHENTICATED`; Marta (outsider) using
  the real identifiers of Ana's project and tasks → `404 NOT_FOUND` on
  every operation, database unchanged, project absent from her search and
  dashboard; Rui (Member) creating a task → `403 FORBIDDEN`, nothing
  created.
- **Associated tests:**
  - Existing (I):
    `ApiAuthorizationTests.outsiderGetsNotFoundForEveryOperationAndChangesNothing`,
    `ApiAuthorizationTests.memberCannotPerformManagerActionsAndChangesNothing`,
    `TaskApiTests.permissionsFollowTheAccessMatrix`,
    `ProjectAccessPolicyTests.nonMembersCannotSeeTheProject`;
    (C) `tasks.test.tsx` "shows tasks read-only to members who are not
    assigned".
  - **Planned (Ex3):** anonymous request to the task endpoints (AU-01).
  - **Planned (Ex5):** security review of IDOR with two demo accounts
    (manual), plus static analysis with oxlint and Semgrep.

## 6. Review of the AI suggestions

The scenario matrix was first drafted with AI assistance and then reviewed
against the documents. Assumptions corrected:

| AI assumption | Correction | Reference |
|---------------|-----------|-----------|
| An outsider receives `403` | `404 NOT_FOUND` (does not reveal that the project exists) | `implementation-plan.md` §4 R2 |
| An invalid title returns `422` | `400 VALIDATION_ERROR` with field errors; `422` only for `ASSIGNEE_NOT_ELIGIBLE` | `openapi.yaml`, ADR-0010 |
| Title of 1–255 characters, checked before trimming | 3–150 after trimming, whitespace-only rejected, counted in code points | `requirements.md` validation limits |
| Any project Member can create tasks | Only Managers and Administrators | FR3, §4 |
| Create takes a single `ownerId` and may set the status | 0..N `assigneeIds`; creator from the session; `status` → `400` | ADR-0013, §4 R1 |
| A "deadline in the past" scenario | Excluded: deadline deferred | FR5, ADR-0011 |
| A Viewer read-only scenario | Excluded: out of scope | `requirements.md` |
| The required-title warning is a UI concern | Also enforced by the API (server-side) | Security NFR, `constraints.md` |
| E2E against the development database | Isolated test database | Section 2 |

## 7. Decisions

- **Feature:** task management, because it carries the main business rule
  (title validation), persistence and the access rules most exposed to
  IDOR.
- **Derive expectations from the documents:** where the documents define
  a result (codes, limits, roles), that is the expected result; the code
  is not taken as the oracle.
- **Reuse existing tests** that already check a scenario; add tests only
  for gaps (TV-04, AU-01, CR-04) or for the levels the exercises require
  (unit, integration, E2E).
- **Outsider vs Member:** both kept, because they test different rules
  (`404` hides the project; `403` refuses an action on a visible one).
- **Not selected scenarios** stay in the matrix with their existing tests,
  as regression coverage.

## 8. Traceability and pending work

| Exercise | Scenarios | Test to add | Status |
|----------|-----------|-------------|--------|
| Ex2 — unit test | TV-01 to TV-07 | Title rule: valid, invalid, boundaries | Done (§9); finding F-01 open |
| Ex3 — integration test | CR-01, CR-02, CR-03, AU-01 | Create + read via API with PostgreSQL; negative case | Planned |
| Ex4 — E2E | CR-04 (CR-02) | Playwright, Chromium, isolated database | Planned |
| Ex5 — review | AU-02 (AU-01 to AU-08); F-01 | Code review, IDOR with two demo accounts, oxlint, Semgrep; review finding F-01 | Planned |

Existing tests referenced above were run on 2026-11-12: `TaskApiTests`
(10), `ApiAuthorizationTests` (3), `ProjectAccessPolicyTests` (8) and
`tasks.test.tsx` (18) — all passed. Unit test results of Ex2 are in §9.

## 9. Unit tests of the title rule (TV-01 to TV-07)

Business rule: task title trimmed, 3–150 characters counted as code
points, whitespace-only rejected (`requirements.md` validation limits;
`TaskCreateRequest.title` in `openapi.yaml`: `minLength: 3`,
`maxLength: 150`, `pattern: '\S'`). Expected values come from these
documents, not from the code.

### 9.1 Tests

Pure unit tests: no Spring context, no database, no shared state, so they
can be repeated in any order without previous data or runs.

| Test | Framework | Rule under test | Cases |
|------|-----------|-----------------|-------|
| `backend/…/tasks/TaskTitleRuleTests` `Create` | JUnit 5, Hibernate Validator (`Validation.buildDefaultValidatorFactory()`) | `TaskCreateRequest` (trim in the record + `@NotNull`/`@CodePointLength`) | Valid (trimmed); missing; `""`, `"   "`, `"\t\n "`; 2 after trim (`"ab"`, `" ab "`, `"  ab\t"`); 3 after trim (`"abc"`, `"  abc  "`, `"\tabc\n"` → `"abc"`); 150, 150 with surrounding spaces; 151; 2/3/150/151 emoji; non-breaking spaces only (TV-07) |
| `TaskTitleRuleTests` `Update` | JUnit 5 | `TaskRules.validate` (edit, `TaskUpdateRequest` trims) | Valid (trimmed); `null`; `""`, `"   "`, `" ab "`, 2 emoji; `"abc"`, `"  abc  "`, 3 emoji; 150 / 151; non-breaking spaces only (TV-07); errors checked as `VALIDATION_ERROR` with field `title` |
| `frontend/src/api/tasks.test.ts` | Vitest | `validateTask` (form validation before calling the API) | Valid; `""`, `"   "`, `"\t\n "`, non-breaking spaces → "Title is required."; 2 after trim and 2 emoji → length message; 3 after trim and 3 emoji accepted; 150 (also with spaces, also emoji) accepted; 151 (also emoji) rejected |

### 9.2 AI-generated tests and corrections

The tests were drafted with AI assistance, run, and then reviewed against
the requirements and the contract:

| Draft (AI) | Problem | Correction |
|------------|---------|------------|
| `Update.titleAtMinimumAfterTrimIsAccepted` expected `title.strip()` | The expected value was computed with the same function the production code uses, so the test could not detect a wrong trim | Literal expected values (`"  abc  "` → `"abc"`) with `@CsvSource` |
| No case for whitespace other than space, tab and newline | `openapi.yaml` defines "not whitespace-only" as `pattern: '\S'` (ECMA-262, where U+00A0 is whitespace) and the UI rejects it; the draft only checked what the code already handles | Added TV-07 (`"\u00A0\u00A0\u00A0"`) to `Create`, `Update` and the frontend tests |
| Frontend draft | Reviewed; assertions match the requirements and the UI messages | No change |

Kept as is after review: `""` on the API returns the length message, not
"Title is required."; the requirements and the contract only require
`400` with field `title`, so this is not a defect.

### 9.3 Runs and results (2026-11-12)

| Command (from the repository root) | Result |
|-----------------------------------|--------|
| `cd backend && ./mvnw -Dtest=TaskTitleRuleTests test` (first draft) | 25 tests, all passed |
| Same, after the corrections of §9.2 | 27 tests: 25 passed, **2 failed** — `Create.noBreakSpaceOnlyTitleIsRejected` ("Expecting actual not to be empty") and `Update.noBreakSpaceOnlyTitleIsRejected` ("Expecting code to raise a throwable") |
| Same, with the two TV-07 tests `@Disabled` (reason: finding F-01) | 27 tests: 25 passed, 2 skipped |
| `cd frontend && npx vitest run src/api/tasks.test.ts` | 18 tests, all passed |
| `cd backend && ./mvnw verify` (Docker running, Testcontainers PostgreSQL 16) | BUILD SUCCESS; 159 test cases in the Surefire reports: 157 passed, 2 skipped (F-01), 0 failed (the console summary counts `@Nested` classes incorrectly: "158 run, 1 skipped") |
| `cd frontend && npm test` | 5 files, 61 tests, all passed |
| `cd frontend && npm run lint` | No warnings or errors |
| `cd frontend && npm run build` | Type check and build succeeded |

Failure analysis: the two failures are an **application defect**, not a
test problem (the expected result comes from the contract and matches the
UI) nor an environment limitation (pure unit tests, reproducible with the
JDK only). Production code was not changed.

### 9.4 Findings

| ID | Finding | Evidence | Status |
|----|---------|----------|--------|
| F-01 | The API accepts a title made only of non-breaking spaces (U+00A0): `Text.trim` uses `String.strip()`, and Java does not treat U+00A0 as whitespace, so `"\u00A0\u00A0\u00A0"` counts as 3 characters. The database check (`char_length(btrim(title))`) only trims spaces, so it is stored. This contradicts "whitespace-only rejected" (`requirements.md`), the contract (`pattern: '\S'`) and the UI (`validateTask` rejects it). The same helper is used for project names, descriptions and comments. | `TaskTitleRuleTests` TV-07 (2 tests, `@Disabled` with reason F-01; fail when enabled) | Open — for review (Ex5); production code not changed |

Coverage alone does not show that the rule is right: all existing tests
passed before TV-07, and the defect was found only by deriving a case from
the contract.
