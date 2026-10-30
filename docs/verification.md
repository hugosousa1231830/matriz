# Matriz — Release 1 verification

Verification of release 1 (Increment 8 of
[`implementation-plan.md`](implementation-plan.md)) against the requirements
in [`requirements.md`](requirements.md) and the acceptance criteria in
[`user-stories.md`](user-stories.md).

- **Date of the run:** 2026-10-29 (Increment 8); automated checks re-run
  after the Increment 9 review fixes and refactor.
- **Code verified:** `master` after Increment 7, plus the tests added in
  Increment 8 (no product code changed); then `master` after Increment 9.
- **Result:** all nine verification cases pass. Increment 8 found no bugs;
  the Increment 9 review found two, fixed with regression tests (section 7).
- **AI usage:** examples of how AI assistance was used, and what was
  accepted, corrected or rejected, are in section 8.

## 1. How to run the checks

From a clean clone, with Docker running (see [`README.md`](../README.md)):

| Check | Command | Kind |
|-------|---------|------|
| Backend tests (unit, slice, MockMvc API with contract validation, Testcontainers PostgreSQL, Flyway, modularity) | `cd backend && ./mvnw verify` | Automated |
| Frontend tests (Vitest + React Testing Library against an in-memory fake API) | `cd frontend && npm test` | Automated |
| Frontend lint | `cd frontend && npm run lint` | Automated |
| Frontend type check and production build | `cd frontend && npm run build` | Automated |
| Generated API types match `openapi.yaml` | `cd frontend && npm run api:check` | Automated |
| Contract lint | `npx @redocly/cli lint openapi.yaml` | Automated |
| Main flows against the running application | Section 4 | Manual |

For the manual run: `docker compose up -d db`, then
`cd backend && ./mvnw spring-boot:run` (local profile, reads `.env`) and
`cd frontend && npm run dev`. Demo accounts and the demo password variable
(`MATRIZ_DEMO_PASSWORD`) are described in the README and `.env.example`.

## 2. Results of the automated checks

| Check | Result (after Increment 9) |
|-------|---------------------|
| `./mvnw verify` | BUILD SUCCESS — 132 tests, 0 failures, 0 errors, 0 skipped (128 at Increment 8) |
| `npm test` | 4 files, 43 tests passed (42 at Increment 8) |
| `npm run lint` | No problems |
| `npm run build` | Succeeded |
| `npm run api:check` | Generated types up to date |
| Redocly lint | Valid (1 problem explicitly ignored in `redocly.yaml`) |

Backend tests per class: `AuthenticationTests` 21, `ProjectAccessTests` 34,
`ProjectApiTests` 10, `TaskApiTests` 10, `MemberApiTests` 8,
`ProjectAccessPolicyTests` 8, `MigrationTests` 8, `AssigneeApiTests` 7,
`CommentApiTests` 7, `DemoDataSeederTests` 6, `DashboardApiTests` 5,
`ApiAuthorizationTests` 3, `ApplicationSmokeTests` 3, `ContractTests` 1,
`ModularityTests` 1. Frontend: `App.test.tsx` 6,
`projects.test.tsx` 8, `tasks.test.tsx` 18, `ui.test.tsx` 11.

Every MockMvc API test also checks the request and response against
`openapi.yaml` (`matchesContract()`).

### Tests added in Increment 8

| Test | Covers |
|------|--------|
| `ApiAuthorizationTests.outsiderGetsNotFoundForEveryOperationAndChangesNothing` | Case 08: a user who is not a member (but is a Manager elsewhere) gets `404` on 18 operations on another project, its tasks, members and comments (reads and changes, using the real identifiers); the project is absent from their search and dashboard; database state is unchanged. |
| `ApiAuthorizationTests.memberCannotPerformManagerActionsAndChangesNothing` | Case 08: a Member gets `403` on 10 Manager actions; state unchanged; the Member can still read and edit the task assigned to them. |
| `ApiAuthorizationTests.identityAndOwnershipCannotBeSuppliedByTheClient` | Case 08: client-supplied `ownerId`, `authorId` and `projectId` are rejected (`400`); a non-member assignee is rejected (`422 ASSIGNEE_NOT_ELIGIBLE`). |
| `DashboardApiTests.editsOfStatusAndPriorityKeepTaskFiltersAndCountsConsistent` | Case 05: after status and priority edits the task, the status and priority filters, the project counts and the dashboard agree. |
| `tasks.test.tsx` — "keeps the version read when editing started, whatever background refreshes load" | Cases 05/09: regression for the Increment 7 synchronization fix — a background refresh during editing does not replace the version sent, so a concurrent change still yields `409 VERSION_CONFLICT` and no false conflict occurs otherwise. Checked by mutation: sending the latest cached version makes the test fail. |
| `tasks.test.tsx` — "shows no success message and keeps the input when saving fails" | Case 09: a `500` on task edit shows an error, no success message, and keeps the typed values. |

The three minimum automated tests required for Increment 8 are, among
others:

- **Validation rule:** `TaskApiTests.titleDescriptionAndPriorityAreValidated`
  (empty, whitespace-only, too short/long and unknown values → `400` with
  field errors, nothing stored).
- **Authorization scenario:**
  `ApiAuthorizationTests.outsiderGetsNotFoundForEveryOperationAndChangesNothing`.
- **Create + persistence read-back:**
  `TaskApiTests.createdTaskHasDefaultsAndIsPersisted` (create through the
  API, then read back through the API and directly from PostgreSQL).

## 3. Verification cases

Manual observations come from the run in section 4 (API through `curl`
with real session cookie and CSRF token, and the UI in a browser).

| # | Scenario | Expected result | Observed result | Evidence |
|---|----------|-----------------|-----------------|----------|
| 01 | Access | Valid credentials sign in; invalid credentials and unauthenticated requests are handled. | As expected. Manual: `/auth/me` without session `401`; wrong password `401` "Invalid email or password"; valid sign-in `200` and `/auth/me` `200`. UI: wrong password shows "Invalid email or password." and stays on sign-in; valid sign-in opens the dashboard. | Automated: `AuthenticationTests` (19, incl. `validCredentialsStartSessionAndReturnProfile`, `invalidCredentialsGetTheSameGeneric401`, `requestsWithoutSessionAreUnauthenticated`, lockout, throttling, session expiry, CSRF), `ApplicationSmokeTests.apiRequiresAuthentication`; `App.test.tsx` (6). Manual: §4 step 1, UI step 1. |
| 02 | Projects | Create, search, edit and archive/unarchive behave according to permissions. | As expected. Manual: create `201` (name trimmed); search finds it (1 item); edit `200`; edit by a non-member `404`; archive → `ARCHIVED`; edit while archived `409 PROJECT_ARCHIVED`; unarchive → `ACTIVE`; `DELETE` `405`. | Automated: `ProjectApiTests` (9), `ProjectAccessPolicyTests` (8), `MemberApiTests` (8); `projects.test.tsx` (8). Manual: §4 step 2. Projects are archived instead of deleted (recorded difference in `requirements.md`, [ADR-0007](adr/0007-soft-delete-projects.md)); `projectsCannotBeDeleted` checks the `405`. |
| 03 | Task persistence | A new task appears without reload and survives a backend restart. | As expected. Manual: create `201`, listed immediately; after stopping and restarting the backend the existing session was still valid (sessions are stored in PostgreSQL) and the task was read back with its latest status, priority, version and assignee. UI: the new task appeared in the list and the counts changed (5 → 6 tasks) without reload. | Automated: `TaskApiTests.createdTaskHasDefaultsAndIsPersisted`, `createReturnsLocationOfTheNewTask`; `tasks.test.tsx` "creates a task after validating the form, and refreshes the list and counts"; `ui.test.tsx` "confirms a created task". Manual: §4 steps 3 and 10, UI step 2. Restart: manual only. |
| 04 | Validation | Empty, whitespace-only and invalid values are rejected. | As expected. Manual: project name `"   "` and `"ab"` → `400`; task title `"   "` → `400` (field `title`); priority `URGENT` and status `LATE` → `400`; comment `"   "` → `400` (field `body`); task count unchanged. UI: title "ab" → "Title must have between 3 and 150 characters." without calling the API. | Automated: `TaskApiTests.titleDescriptionAndPriorityAreValidated`, `ProjectApiTests.nameAndDescriptionAreValidated`, `CommentApiTests.bodyIsValidated`, `AssigneeApiTests.requestIsValidated`, `AuthenticationTests.malformedSignInIsRejectedWithFieldErrors`, `MigrationTests.rejectsWhitespaceOnlyTaskTitle`/`rejectsUnknownTaskStatus` (database constraints); frontend form validation tests in all four test files. Manual: §4 step 4, UI step 2. |
| 05 | Status and priority | Editing updates the task, filters and counts consistently. | As expected. Manual: assignee sets `IN_PROGRESS` (`200`, version 2); a stale edit with version 1 → `409 VERSION_CONFLICT`; `DONE` + `LOW` → `200`; filters `DONE` = 1, `TODO` = 0, `LOW` = 1; project counts done 1/1 (100 %); dashboard entry identical. UI: inline status change to Done → counts 2 done of 6 (33 %), unchanged after page reload. | Automated: `DashboardApiTests.editsOfStatusAndPriorityKeepTaskFiltersAndCountsConsistent` (new), `TaskApiTests.staleVersionIsRejectedAndPartialUpdatesApplyOnlySentFields`, `listFiltersSortsAndPaginates`; `tasks.test.tsx` "changes the status inline and refreshes the project counts", "filters by status, priority and assignee…", version-conflict tests. Manual: §4 step 5, UI step 3. |
| 06 | Assignees | Assign and unassign work; the same assignment is never duplicated. | As expected. Manual: assign `201`; same assignment again `409 ALREADY_ASSIGNED`; non-member `422 ASSIGNEE_NOT_ELIGIBLE`; unassign `204`; reassign `201`. | Automated: `AssigneeApiTests` (7, incl. `duplicateAssignmentIsRejected`, `onlyActiveProjectMembersAreEligible`), `MigrationTests.rejectsDuplicateAssignment` (unique constraint); `tasks.test.tsx` assignee tests (3). Manual: §4 step 6. |
| 07 | Comments | Content, authenticated author and date are stored and shown. | As expected. Manual: Member comment `201` with author "Rui Pereira" and trimmed body; supplying `authorId` → `400`; after the restart the comment was listed with its `createdAt`. | Automated: `CommentApiTests.commentGetsAuthorAndDateFromTheServerAndIsPersisted`, `authorAndDateCannotBeSupplied`, `commentsAreListedOldestFirstAndPaginated`; `tasks.test.tsx` "validates, posts and lists comments with author and date". Manual: §4 steps 7 and 10. |
| 08 | API authorization | Another user cannot read or change resources without permission, even with changed identifiers. | As expected. Manual: an outsider (Manager of another project) using the real identifiers gets `404` on project read, task read, task edit and comment; search returns 0; a Member gets `403 FORBIDDEN` on task delete and task creation; anonymous `401`; the task was unchanged (version 3). | Automated: `ApiAuthorizationTests` (3, new), `ProjectAccessPolicyTests.nonMembersCannotSeeTheProject`, `unknownProjectsAndTasksOfOtherProjectsAreNotFound`, `TaskApiTests.permissionsFollowTheAccessMatrix`, `ProjectApiTests.managersEditMembersCannotOutsidersDoNotSeeIt`, `ApplicationSmokeTests.unknownNonApiPathsAreDenied`. Manual: §4 step 8. |
| 09 | Deletion and errors | Deletion respects dependents; API errors show no success message and keep form data. | As expected. Manual: Member delete `403`; Manager delete `204`; read afterwards `404`; in PostgreSQL the task's assignments and comments were removed with it and project members were kept. | Automated: `TaskApiTests.deleteRemovesAssignmentsAndCommentsInOneGo`, `CommentApiTests.commentsGoWithTheirTask`, `MemberApiTests.removingAMemberDeletesTheirAssignmentsButKeepsCommentsAndTasks`, `MigrationTests.deletingTaskCascadesToAssignmentsAndComments`; `tasks.test.tsx` "shows no success message and keeps the input when saving fails" (new), "keeps the typed comment when the server rejects it", "edits a task and, after a version conflict, reloads without losing the typed input"; `projects.test.tsx` "validates the form … keeps the input on server errors"; `ui.test.tsx` server/network error tests. Manual: §4 step 9. |

## 4. Manual verification run

Run on 2026-10-29 against the local profile (PostgreSQL in Docker Compose,
backend on the port set in `.env`, frontend dev server), with the demo
accounts: Ana (Manager of "Website Redesign"), Rui (Member), Marta (Manager
of other projects, not a member of the project used). A dedicated test
project was created by Ana for the API steps; a leftover project from an
earlier smoke check was deleted from the local database first.

API steps (`curl`, cookie jar, `X-XSRF-TOKEN` header from `GET /auth/csrf`):

1. Access — anonymous `/auth/me`; wrong password; valid sign-in; `/auth/me`.
2. Projects — create (padded name), search, edit, non-member edit, archive,
   edit while archived, unarchive, `DELETE`, add Rui as Member.
3. Tasks — create (padded title, `HIGH`), list.
4. Validation — blank and short project names, blank task title, unknown
   priority and status, blank comment; task count rechecked.
5. Status and priority — Rui (assignee) to `IN_PROGRESS`; Ana with the stale
   version; `DONE` + `LOW`; filters; project counts; dashboard.
6. Assignees — assign, duplicate, non-member, unassign, reassign.
7. Comments — Rui comments; `authorId` in the body.
8. Authorization — Marta on project, task, task edit, comment, search; Rui
   deletes the task and creates a task (Member); anonymous request; task rechecked.
9. *(after step 10)* Deletion — Rui deletes (refused), Ana deletes, read
   back; row counts in PostgreSQL (`docker compose exec db psql`).
10. Restart — backend stopped and started again; the same session cookies
    used to read the task, its assignee and its comments.

UI steps (browser, signed in as Ana):

1. Wrong password → "Invalid email or password."; valid password → dashboard
   with the active project, per-status counts and % done (the archived test
   project not shown).
2. Project page → "New task": title "ab" → field error, no request; title
   with surrounding spaces → "Task … created.", the trimmed title at the top
   of the list and counts 5 → 6 tasks, without reload.
3. Inline status change of the new task to Done → counts updated; still
   2 done of 6 after a page reload.

All steps gave the expected result (section 3). The test project and the
UI task were removed from the local database afterwards.

## 5. Limitations and what was not run

- **No browser end-to-end automation.** The UI is covered by Vitest + React
  Testing Library against an in-memory fake API, and by the short manual
  browser run above; there is no Playwright/Cypress suite in release 1.
- **Backend restart** (case 03) is verified manually only; automated tests
  cover persistence by reading back from PostgreSQL.
- **k6 load test** was not run: it is deferred in the release 1 scope
  (`requirements.md`, `implementation-plan.md` §3), so the performance
  targets are not verified yet.
- **Contract validator gap:** comma-separated array query parameters are not
  parsed by the request validator; that check is ignored and the values are
  covered by `TaskApiTests.listFiltersSortsAndPaginates`.
- **Rate limiting** is in memory and per instance (single instance in
  release 1); the account lockout is stored in the database. Both are
  tested with time and counters controlled in `AuthenticationTests`, not
  across instances.
- **Accessibility** was checked through the tests (labels, focus, dialogs)
  and manually in passing; no automated accessibility audit was run.
- **Environment note:** the Maven build downloads from Maven Central; where a
  corporate mirror is configured and unreachable, run Maven with a settings
  file that uses Maven Central.
- **Docker clock:** if the Docker VM clock drifts from the host clock (seen
  on macOS after sleep), the lockout tests in `AuthenticationTests` fail;
  resynchronise the VM clock (or restart Docker) and re-run.
- **Session re-check cost:** every authenticated request now reads the user
  once (primary key lookup) to apply deactivation and role changes at once;
  not measured under load (k6 deferred).

## 6. Bugs found

Increment 8: none. During the manual run some odd responses were traced to
quoting mistakes in the local helper script (not part of the repository),
not to the application. Bugs found in the Increment 9 review are in
section 7.

## 7. Increment 9 review

The full release 1 diff was reviewed (with AI assistance, then checked by
hand) with attention to permissions, validation, error handling, duplication,
naming, dead code and out-of-scope changes.

| # | Finding | Cause | Fix | Regression test |
|---|---------|-------|-----|-----------------|
| B1 | A deactivated user kept access to every endpoint except `/auth/me` until the session expired; removing a user's Administrator role took effect only at the next sign-in. The contract already says a deactivated user gets `401`. | The authenticated user was stored in the session at sign-in and only `/auth/me` re-read it. | `CurrentUserFilter` re-reads the session user on every request: missing or deactivated → session invalidated, `401`; changed name or role → principal refreshed. | `AuthenticationTests.deactivatedUserLosesAccessToEveryEndpoint`, `revokedAdministratorRightsApplyToTheCurrentSession` |
| B2 | A project name, task title or comment made of emoji could pass validation and then fail the database check, returning `500`. | Bean Validation `@Size` and the rules counted UTF-16 units; PostgreSQL `char_length` and JSON Schema `maxLength` count code points. | `@CodePointLength` and `Text.length()` in the backend, `textLength()` in the frontend forms: all limits count Unicode code points. | `ProjectApiTests` / `TaskApiTests.lengthsCountCharactersNotUtf16Units`, `CommentApiTests.bodyIsValidated`, `ui.test.tsx` "form validation" |

Both regression tests were checked by mutation: with the filter disabled or
UTF-16 counting restored, they fail.

**Refactor (separate commit, behaviour-preserving):** the
microsecond-truncated clock read duplicated in the project, task and comment
services moved to `shared/Timestamps.now`; the unused `ProjectAccess.can`
(which ignored task assignment and could mislead a future caller) was
removed. Verified by the unchanged backend suite (132 tests passing before
and after). Other candidates were left as they are because the gain was
small compared with the risk of touching tested code at delivery: the
duplicated row mappers in `TaskStatistics` and the `instant(rs, col)`
helpers in two query classes (module-private, two lines each), and the
"can edit task" rule repeated in two frontend components.

**Out-of-scope check:** no deferred feature (`requirements.md` "MVP scope",
plan §2) was added; the contract did not need changes (it already
specified both behaviours).

## 8. AI usage

AI assistance was used for planning, implementation, tests, review and
documentation. Three representative examples:

1. **Diff review for access rules.**
   - *Goal of the request:* review the whole release 1 diff for gaps in
     authentication, authorization and error handling.
   - *Decision:* accepted finding B1 (deactivated users keep access) because
     it contradicted the contract (`401` for a deactivated user); chose a per-request
     re-check over shortening session lifetime, since only the re-check
     enforces the rule immediately.
   - *Verification:* two new integration tests, mutation check (tests fail
     with the filter disabled), full `./mvnw verify`.
2. **Length validation with emoji.**
   - *Goal of the request:* find inputs that pass validation but break at the
     database.
   - *Decision:* the AI proposed two options — map the database check
     violation to `400`, or count code points everywhere. The second was
     chosen: it matches PostgreSQL and the contract's JSON Schema, gives
     field-level messages, and keeps the frontend and backend consistent.
   - *Verification:* new API tests with 4-byte characters at and over the
     limits, frontend form tests, mutation check.
3. **Corrected and rejected suggestions.**
   - *Goal of the request:* write the regression tests and propose
     refactorings.
   - *Decision:* corrected the generated tests — they used a wrong table
     name (`projects` instead of `project`) and applied the contract matcher
     to intentionally invalid requests, which the schema rejects before the
     server rule is reached. When the lockout tests failed, the AI first
     suspected the new filter; running the previous commit showed the same
     failure, and the cause was clock drift in the Docker VM, so no code was
     changed for it. Of the suggested refactorings only two were kept
     (section 7); the rest were rejected as low value at delivery.
   - *Verification:* tests re-run against both the previous commit and the
     fixed code; the full suites pass.
