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
  API (`src/test/fakeApi.ts`). The E2E run (§11) uses its own
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
| End-to-end (E) | Playwright (Chromium) against the whole application in Docker with an isolated database (§11) | Main user flow through the browser |
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
| TV-04 ★ | Title at minimum | `"abc"`, `"  abc  "` | Accepted; stored as `"abc"` | U, I | Covered (U, I) |
| TV-05 ★ | Title at maximum and above | 150 / 151 characters | 150 accepted; 151 → `400` | U, I | Covered (U, I) |
| TV-06 | Length counted in characters (code points) | 2 / 3 emoji | 2 → `400`; 3 accepted | U, I | Covered (U, I) |
| TV-07 | Title of non-breaking spaces only | `"\u00A0\u00A0\u00A0"` | `400`, field `title` (whitespace-only; contract `pattern: '\S'`) | U, I | Covered (U, UI and API); was finding F-01 (§9.4), fixed in Ex5 (`quality-security-review.md`) |
| CR-01 ★ | Manager creates a task and reads it | Ana, title only, *Website Redesign* | `201` with `Location`; `TODO`, `MEDIUM`, version 1, `createdBy` = signed-in user; `GET /tasks/{id}` returns the same task; row in PostgreSQL | I | Covered (I, §10) |
| CR-02 ★ | Created task appears in the project list | `GET /projects/{id}/tasks` | Task listed; project counts `todo` +1 | I, C, E | Covered (I §10, C, E §11) |
| CR-03 ★ | Invalid create is not persisted | Blank title | `400`; task count and list unchanged | I | Covered (I, §10) |
| CR-04 ★ | Create through the UI | Sign in → open project → create → list | New task visible in the list without reload | E | Covered (E, §11) |
| AU-01 ★ | Anonymous access to tasks | No session: `GET`/`POST` task endpoints | `401 UNAUTHENTICATED`; nothing created | I | Covered (I, §10) |
| AU-02 ★ | Outsider uses real identifiers (IDOR) | Marta on a *Website Redesign* project/task: read, list, create, edit, delete | `404 NOT_FOUND` (not `403`); state unchanged; project absent from search and dashboard | I, R | Covered (I, also §10); R done (Ex5): no IDOR found (`quality-security-review.md` §4) |
| AU-03 ★ | Member creates a task | Rui, `POST /projects/{id}/tasks` | `403 FORBIDDEN`; nothing created; UI hides "New task" | I, C | Covered (also §10) |
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
  - Integration (I, §10): `TaskCreateReadIntegrationTests` — create, read
    through `Location`, row checked in PostgreSQL, project list and counts
    (CR-01, CR-02); invalid title and nonexistent task/project (CR-03).
  - End-to-end (E, §11): `frontend/e2e/create-task.e2e.ts` (Playwright,
    Chromium) — sign in → open project → create task → task visible in the
    list without reload, and still listed after a reload (CR-04, CR-02),
    against an isolated test database.

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
  - Integration (I, §10): `TaskCreateReadIntegrationTests` — anonymous
    request to every task endpoint (AU-01, former gap); Member reads but
    cannot create, outsider gets `404` (AU-02, AU-03).
  - Review (R, Ex5): manual IDOR check with the demo accounts (Marta as
    outsider, Rui as Member) on an isolated stack, 37 requests, no
    unauthorized read or change; static analysis with oxlint and Semgrep.
    Results in [quality-security-review.md](quality-security-review.md) §4
    and §6.

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
| Ex2 — unit test | TV-01 to TV-07 | Title rule: valid, invalid, boundaries | Done (§9); finding F-01 fixed in Ex5, TV-07 enabled |
| Ex3 — integration test | CR-01, CR-02, CR-03, TV-04, AU-01 to AU-03 | Create + read via API with PostgreSQL; negative cases | Done (§10); no new finding |
| Ex4 — E2E | CR-04 (CR-02) | Playwright, Chromium, isolated database | Done (§11); no new finding |
| Ex5 — review | AU-02 (AU-01 to AU-08); F-01 | Code review, IDOR with two demo accounts, oxlint, Semgrep; review finding F-01 | Review done ([quality-security-review.md](quality-security-review.md)): no IDOR; F-01 confirmed for all text fields; new findings F-02, F-03. Approved and fixed with regression tests: F-01 (TV-07 re-enabled, `TextTests`, API tests for project name, task title, comment body) and F-02 (nginx no longer forwards a client-chosen `X-Forwarded-For`; Semgrep rule + live re-check); F-03 and S-01 deferred |

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
| `cd backend && ./mvnw verify` after the F-01 fix (Ex5, TV-07 enabled) | BUILD SUCCESS; 179 test cases in the Surefire reports, 0 failed, 0 skipped (27 in `TaskTitleRuleTests`) |

Failure analysis: the two failures are an **application defect**, not a
test problem (the expected result comes from the contract and matches the
UI) nor an environment limitation (pure unit tests, reproducible with the
JDK only). Production code was not changed.

### 9.4 Findings

| ID | Finding | Evidence | Status |
|----|---------|----------|--------|
| F-01 | The API accepts a title made only of non-breaking spaces (U+00A0): `Text.trim` uses `String.strip()`, and Java does not treat U+00A0 as whitespace, so `"\u00A0\u00A0\u00A0"` counts as 3 characters. The database check (`char_length(btrim(title))`) only trims spaces, so it is stored. This contradicts "whitespace-only rejected" (`requirements.md`), the contract (`pattern: '\S'`) and the UI (`validateTask` rejects it). The same helper is used for project names, descriptions and comments. | `TaskTitleRuleTests` TV-07 (2 tests, failed when enabled); live API check in [quality-security-review.md](quality-security-review.md) F-01 | **Fixed** in Ex5 after human approval: `Text.trim` also removes Unicode space separators and U+FEFF; TV-07 re-enabled and passing; regression tests `TextTests` and API tests for project name, task title and comment body (see the review, F-01) |

Coverage alone does not show that the rule is right: all existing tests
passed before TV-07, and the defect was found only by deriving a case from
the contract.

## 10. Integration test of creating and reading a task (CR-01 to CR-03, TV-04, AU-01 to AU-03)

Goal: check the API together with real persistence in a controlled
environment. Expected values come from FR3/US3, §4 of
`implementation-plan.md`, ADR-0010 and `openapi.yaml`, not from the code.

### 10.1 Test and environment

`backend/…/tasks/TaskCreateReadIntegrationTests` extends `ApiIntegrationTest`:
full application, MockMvc through the real sign-in, session cookie and CSRF
(no security shortcuts), PostgreSQL 16 in a Testcontainers container with
the Flyway migrations and the demo seed. The create, read, list, error
and permission responses are checked against `openapi.yaml` (request and
response; response only for requests that break the contract on purpose,
§10.4).

| Test | Scenarios | Checks |
|------|-----------|--------|
| `managerCreatesTaskAndReadsItBackFromApiAndDatabase` | CR-01, CR-02 | Ana creates a task (unique title): `201`, JSON body, `Location` ends with `/api/v1/tasks/{id}`; `TODO`, `MEDIUM`, no assignees, version 1, `description` `null`, `createdBy.id` = Ana's id from the database; `GET` on the `Location` URL returns exactly the same object; the `task` row has the same project, title, status, priority, creator and version, and no `task_assignee` rows; total task count +1; listed in `GET /projects/{id}/tasks` and counted in `taskCounts.todo` |
| `titleAtMinimumIsTrimmedBeforeItIsStored` | TV-04 (I) | `"  abc  "` → `201` with `"abc"`, `GET` returns `"abc"`, column `title` = `"abc"` |
| `invalidTitleIsRejectedAsProblemAndNothingIsStored` | CR-03 (negative) | `"   "`, `" ab "` and missing title → `400`, `application/problem+json`, `status` 400, `code` `VALIDATION_ERROR`, `errors[0].field` = `title`, response valid against the contract; no task in the project, total count unchanged, list empty |
| `nonexistentTaskOrProjectAnswersNotFoundAndStoresNothing` | Nonexistent resource (negative) | `GET /tasks/{random id}` and `POST /projects/{random id}/tasks` → `404 NOT_FOUND` (problem+json); total count unchanged |
| `anonymousCallerGetsUnauthenticatedOnTaskEndpointsAndChangesNothing` | AU-01 (former gap) | No session (valid CSRF token, so the request reaches authentication): list, read, create, edit and delete → `401 UNAUTHENTICATED`; the existing task is unchanged (title, version 1) and nothing is created |
| `createAndReadFollowTheProjectRoles` | AU-02, AU-03 | Rui (Member) reads the task (`200`) but cannot create one (`403 FORBIDDEN`); Marta (outsider) with the real identifiers → `404 NOT_FOUND` on read, list and create; only the original task exists |

AU-02 and AU-03 were already covered by `TaskApiTests` and
`ApiAuthorizationTests` (§5.3); they are repeated here only for the create →
read flow. The remaining scenarios of the matrix keep their existing tests.

### 10.2 Isolation and repeatability

- **Test database:** a PostgreSQL container started for the run and
  discarded afterwards; the development database is never used.
- **Own data per test:** each test creates its own project with a unique
  name (and unique task title where it matters) through the API, and asserts
  only on that project or on counts relative to the start of the test; it
  does not rely on data, order or results of other tests or earlier runs.
- **Clean-up:** a test transaction cannot be rolled back here, because the
  application commits each MockMvc request in its own transaction. Instead,
  `@AfterEach` deletes the tasks, memberships and projects the test created
  (assignments and comments cascade) and asserts that the `task` and
  `project` counts are back to the values recorded in `@BeforeEach`, so
  shared classes in the same run see the seed state again.
- **Check of the clean-up:** with the deletes temporarily disabled, 5 of 6
  tests failed on "tasks/projects after cleanup" (the sixth creates no
  project), so the check detects leftover data. The deletes were restored
  before the runs below.

### 10.3 Runs and results (2026-11-12)

| Command (from the repository root) | Result |
|-----------------------------------|--------|
| `cd backend && ./mvnw -Dtest=TaskCreateReadIntegrationTests test` (first draft) | 6 tests: 5 passed, **1 error** — `invalidTitleIsRejectedAsProblemAndNothingIsStored`: `OpenApiValidation` "does not match the regex pattern \S" at `REQUEST /title` |
| Same, after the correction of §10.4 (run 1) | 6 tests, all passed |
| Same, deletes temporarily disabled (clean-up check, §10.2) | 6 tests: 1 passed, 5 failed on the clean-up assertion |
| Same, deletes restored (run 2) | 6 tests, all passed |
| `cd backend && ./mvnw verify` (Docker running, Testcontainers PostgreSQL 16) | BUILD SUCCESS; 165 test cases in the Surefire reports: 163 passed, 2 skipped (F-01), 0 failed (console: "164 run, 1 skipped", see §9.3). Before the Ex5 fix; after it: 179, 0 skipped (§9.3) |

Maven ran offline (`-o`) with the local repository; no settings file was
needed.

### 10.4 Failure analysis and corrections

| Failure | Classification | Correction |
|---------|----------------|------------|
| `invalidTitleIsRejected…` failed in `matchesContract()` | **Test problem**: `matchesContract()` validates the request as well as the response, and the negative case sends a request that breaks the contract on purpose (`pattern: '\S'`). The application answered as expected (`400 VALIDATION_ERROR`) | For negative cases the test validates only the response against `openapi.yaml` (`responseMatchesContract()`) |

No application defect and no environment limitation were found in this
exercise; F-01 (§9.4) remains open for Ex5. Production code was not
changed. (Written before the Ex5 fixes; F-01 is now fixed — see §9.4 and §12.) These tests check the documented behaviour of one flow; passing
them (or coverage) does not show that untested requirements are correct.

## 11. End-to-end test of creating a task through the UI (CR-04, CR-02)

Goal: validate one user flow through the real interface — sign in → open
project → create task → task in the list. Expected results come from US3
(FR3: a Manager creates a task, default status To do and priority Medium)
and CR-04 above, not from the code.

### 11.1 Test and environment

| Item | Choice |
|------|--------|
| Tool | Playwright `@playwright/test` 1.65, Chromium (Playwright's headless shell, installed with `npx playwright install chromium`) |
| Test | `frontend/e2e/create-task.e2e.ts`; configuration `frontend/playwright.config.ts`; command `cd frontend && npm run test:e2e` |
| Application under test | The real images of `compose.yaml` (nginx with the production build of the SPA, the API with the `local` profile, PostgreSQL 16), started as the separate Compose project `matriz-e2e` with the override `frontend/e2e/compose.e2e.yaml`; SPA on `http://127.0.0.1:5180` (`MATRIZ_E2E_WEB_PORT`) |
| Test database | PostgreSQL in a `tmpfs` inside the `matriz-e2e` project: never the development volume `matriz-db-data`; database and API ports not published |
| Test data | Demo seed (`MATRIZ_SEED_DEMO=true`), account `ana@matriz.test` (Manager of *Website Redesign*); database credentials and demo password from the environment or, if unset, the fictitious values of `.env.example` (the developer's `.env` is not read for them) |
| Start and stop | `e2e/global-setup.ts`: `docker compose … down --volumes`, then `up --detach --build --wait` (the backend reports healthy only when `/actuator/health`, with the readiness state added, is `UP`, i.e. after migrations and the seed); `e2e/global-teardown.ts`: `down --volumes` (`E2E_KEEP_STACK=1` keeps the stack to inspect a failure) |
| Evidence | Trace and screenshot only on failure (`trace: 'retain-on-failure'`, `screenshot: 'only-on-failure'`) in `frontend/test-results/`; HTML report in `frontend/playwright-report/` (both git-ignored) |

Steps and assertions (all through the UI, no API calls from the test):

| Step | Selectors (from the real UI) | Assertion |
|------|------------------------------|-----------|
| Sign in | `getByLabel('Email')`, `getByLabel('Password')`, button "Sign in" | Heading "Dashboard" (level 1) |
| Open project | Link "Projects" in navigation "Main", link "Website Redesign" | Headings "Projects" and "Website Redesign" (level 1); text "Your role: Manager"; table "Tasks of the project" has no link with the new title yet |
| Create task | Button "New task"; in region "New task": `getByLabel('Title')`, `getByLabel('Description')`, button "Create task" | Message "Task “…” created."; the form closes |
| Task in the list | Row of table "Tasks of the project" with the link named exactly as the title | Exactly one row; status control "Status of …" = `TODO` (To do); assignees "Unassigned"; priority "Medium" — without reloading |
| Stored | `page.reload()` | The link is still listed (the task came from the server, not from page state) |

Stable selectors and no fixed waits: roles, accessible names and labels
only (no CSS classes or generated ids); every wait is a web-first assertion
(`expect(…).toBeVisible()`, `toHaveValue`, `toHaveText`, `toHaveCount`) or
the stack's health check; no `waitForTimeout`.

Isolation and repeatability: each run starts a new database with the demo
seed and removes it at the end; the task title is unique per run
(`E2E task <timestamp>`), so the assertions never depend on a task left by
another run, and the test also passes several times in the same database
(`--repeat-each=3`, below).

### 11.2 Runs and results (2026-11-12)

Docker Desktop (Engine 28.5.1, Compose 2.40.2), macOS, Node.js 25.2.1.

| Command (from `frontend/`) | Result |
|----------------------------|--------|
| `npm i -D @playwright/test` and `npx playwright install chromium` | Installed Playwright 1.65.0; Chromium headless shell downloaded (no proxy limitation) |
| `npm run test:e2e` (run 1, images built for the `matriz-e2e` project) | 1 passed (test 1.1 s; 1.7 min in total with the image build); stack removed |
| Same, with the priority assertion temporarily changed to "High" (check that the test detects a wrong result) | 1 failed: `toHaveText` expected "High", received "Medium"; screenshot, trace and error context kept in `test-results/`; stack removed. Assertion restored |
| `npm run test:e2e` (run 2) | 1 passed (0.85 s; 15.6 s in total) |
| `npm run test:e2e` (run 3) | 1 passed (0.98 s; 15.8 s in total); `test-results/` holds no trace or screenshot (passed) |
| `npx playwright test --repeat-each=3` (three times in the same database) | 3 passed (18.2 s in total) |
| `npm run test:e2e` with the repository's `.env` moved aside (run 4) | 1 passed (0.84 s; 16.3 s in total): the run needs no `.env` |
| `npm test` | 5 files, 61 tests, all passed (the `*.e2e.ts` files are not picked up by Vitest) |
| `npm run lint` | No warnings or errors |
| `npm run build` | Type check (now also of `playwright.config.ts` and `e2e/`) and build succeeded |
| `npm run api:check` | No difference with `openapi.yaml` |

After the runs, no `matriz-e2e` container, network or volume remained and
the development volume `matriz-db-data` was not used.

### 11.3 Failure analysis

No failure of the test against the application: no application defect,
test problem or environment limitation was found. The only failure was the
intended one of §11.2 (test changed on purpose), diagnosed from the error
message alone. F-01 (§9.4) remains open for Ex5. Production code was not
changed. (Written before the Ex5 fixes; F-01 is now fixed — see §9.4 and §12.) One E2E test checks one flow; it does not show that other flows or
untested requirements are correct.

## 12. Delivery status: tests not executed and pending problems

Delivered on branch `master` (not pushed when written; `master` was later
published to GitHub with CI — see [delivery.md](delivery.md) §8). The test and review work is in
the test-plan commits (`95abb30` and its predecessors); the last code change is
`5766655` (F-02 fix), followed only by documentation updates (see
`git log`). All results above come from real runs on 2026-11-12; an
independent re-run at `5766655` reproduced them (`./mvnw verify` 179 tests,
0 failed, 0 skipped; `npm test` 61 passed; lint, build and `api:check`
clean; `npm run test:e2e` 1 passed).

Not executed:

- E2E flows other than CR-04 (edit, delete, assignees, comments, permission
  denials in the UI); these are covered at API and component level only.
- Load or performance tests; dynamic scanners (e.g. OWASP ZAP), dependency
  (SCA) and container image scans — none is configured.
- The sign-in rate limit in the development setup (`./mvnw
  spring-boot:run` behind the Vite proxy); see the review §2.
- `scripts/semgrep.sh` downloads registry rulesets and needs
  `MATRIZ_EXTRA_CA_CERT` behind a TLS-inspecting proxy; without it only the
  project rules (`.semgrep.yml`) can run offline.

Pending problems (accepted, see [quality-security-review.md](quality-security-review.md) §8):

- F-03 (low): project search length counts UTF-16 units — deferred.
- S-01: no security headers on SPA responses from nginx — deferred until a
  deployment is defined.
- The F-02 regression guard is a Semgrep rule, so it runs only with
  `scripts/semgrep.sh` (not part of the CI workflow added later, decision D-05 in
  [delivery.md](delivery.md)).
- The E2E test reads table cells by position (`nth(1)`, `nth(3)`); a change
  of column order would need the test updated.
