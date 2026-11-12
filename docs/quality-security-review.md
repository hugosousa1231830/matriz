# Matriz — Code review and security review (release 1)

Results of the code review and the security review of the last increment and
of the recent changes: what was checked, the evidence, the findings and the
decisions about them. Findings were proposals for a human decision; the
review itself changed no production code. After the review, the project owner
approved fixing F-01 and F-02 only; §8 records the decisions, the fixes and
their regression tests.

Date: 2026-11-12. Reviewer: coding agent (GitHub Copilot) acting as senior
software engineer and application security reviewer; decisions by the
project owner.

## 1. Scope

| Part | What | Why |
|------|------|-----|
| Code review | The last delivered increment, **Increment 9 — Review and delivery** (`1053e46`, `927af1e`, `ed273b5`, `1ecbfe9`, `82adc04`, `75da9b1`), and the recent diff after it, the Ficha 4 test changes (`24a7718`, `1d101ec`, `783ef1b`, `b33d478`) | Increment 9 is the last increment in `implementation-plan.md` §1 and contains the last production-code changes: session user re-check (`CurrentUserFilter`), code-point length rules (`Text`, `textLength`), the `Timestamps` refactor and the Docker Compose stack (`docker-compose.yml`, Dockerfiles, `nginx.conf`). The Ficha 4 commits only add tests and docs; they are reviewed for missing or weak tests. |
| Security review | Access to projects and tasks (and what hangs off them: members, member candidates, assignees, comments, search, dashboard) for the whole API, not only the diff | The exercise asks for IDOR / broken access control on projects and tasks; the authorization rules are in `implementation-plan.md` §4 (R1–R15) and ADR-0012. |
| Also checked | Secrets in the repository and its history, data exposure in responses and errors, authentication hardening touched by the diff (rate limit behind the new proxy) | Requested by the review prompt. |

Focus: bugs, validation, error handling and missing tests. Style-only
remarks are left out.

## 2. Checks executed and not validated

| # | Check | How | Result |
|---|-------|-----|--------|
| C1 | Read the scope commits and the authorization code paths | `git show` of the commits above; every controller/service of `projects`, `tasks`, `comments`, `dashboard`; `ProjectAccessPolicy`, `ProjectAccess`, `TaskLookup`, `SecurityConfig`, `CurrentUserFilter`, `AuthController`, `SignInService`, `SignInRateLimiter`, `ApiExceptionHandler`, the SQL query builders | Every project-scoped service method calls `ProjectAccessPolicy` (`require`, `requireTask` or `access`) with the session user (§4.1). |
| C2 | Manual IDOR check with demo accounts on a running stack | Isolated Compose project `matriz-ex5` (images built from `HEAD` `b33d478`, throw-away database, demo seed, SPA on `127.0.0.1:5181`, `/api` proxied by nginx) with `curl` (session cookie + CSRF header) | 37 requests; no unauthorized read or change (§4.2). |
| C3 | Live input checks for F-01 | Same stack, `curl` as Ana | Confirmed for task title/description, project name/description and comment body (§3, F-01). |
| C4 | Live check of the sign-in rate limit behind nginx | Same stack, `curl` with and without `X-Forwarded-For` | Bypass confirmed (§3, F-02). |
| C5 | Data exposure in responses and errors | Same stack: problem bodies of `400`/`401`/`403`/`404`, cookies and security headers, actuator and API-docs paths | No internal details, stack traces or other users' data; only `health` exposed (§5). |
| C6 | Static analysis, oxlint | `cd frontend && npm run lint` and an extended run | 0 findings (default); 4 non-security warnings in the extended run, triaged (§6.1). |
| C7 | Static analysis, Semgrep (added in this review) | `scripts/semgrep.sh` (Semgrep 1.184.0, project rules `.semgrep.yml` + 9 registry rulesets, metrics off) | 20 findings: 1 confirmed (F-01), 19 false positives (§6.2). |
| C8 | Secrets in the tree and in the history | Pattern search over the working tree and `git log -p --all` (128 commits) for keys, tokens, passwords, private keys, connection strings | Only the documented fictitious values (§7). |
| C9 | Existing automated authorization tests | Mapped to the access matrix (§4.3); they ran green in Ex1–Ex3 (`docs/test-plan.md` §8–§10) | Not re-run during the review (no code changed); re-run with the fixes (C10). |
| C10 | Verification of the approved fixes (F-01, F-02) | `./mvnw verify`; `npm test`, `npm run lint`, `npm run build`, `npm run api:check`; `npm run test:e2e`; `scripts/semgrep.sh`; the IDOR script (C2) and the live checks C3 and C4 on a new isolated stack built from the fixed tree | All green; IDOR statuses identical to C2 for every request; F-01 cases now `400 VALIDATION_ERROR`; F-02 step B now `429` (§8). |

Not validated (out of reach or out of scope of this review):

- The rate-limit behaviour in the **development** setup (`./mvnw
  spring-boot:run` behind the Vite proxy) was not run; from the
  configuration it is not affected (no `server.forward-headers-strategy`,
  so `X-Forwarded-For` is ignored).
- Session fixation, session timeout and lockout were not re-tested live;
  they are covered by `AuthenticationTests` (Increment 3) and
  `verification.md`.
- No dynamic scanner (e.g. OWASP ZAP), no dependency vulnerability scan
  (SCA) and no container image scan were run; none is configured in the
  project.
- Administrator access was checked only as a control (Vitor reads the
  project); administrator-only paths were not attacked separately.
- The browser UI was not used for the security review; the UI only hides
  actions, the API is the security boundary.

## 3. Findings

Severity: **High** (exploitable, data of others exposed or changed),
**Medium** (a documented control is defeated or invalid data is accepted),
**Low** (minor deviation, hardening), **Info** (observation, no action
proposed). "Confirmed" means reproduced by a run (test, request or tool
output); "hypothesis" means derived from reading code or configuration only.

| ID | Title | Severity | Status of evidence |
|----|-------|----------|--------------------|
| F-01 | Whitespace-only text made of Unicode spaces is accepted | Medium | Confirmed (unit tests, live API, Semgrep); **fixed** (§8) |
| F-02 | Sign-in rate limit bypassed with a forged `X-Forwarded-For` behind nginx | Medium | Confirmed (live); **fixed** (§8) |
| F-03 | Search length limits count UTF-16 units, not code points | Low | Confirmed by code reading; not run; deferred (§8) |
| S-01 | SPA responses from nginx have no security headers and show the nginx version | Low | Observed (live); impact is a hypothesis |
| S-02 | Any user can list all active users' names and emails | Info | Observed; matches the documented rules |
| S-03 | Session and CSRF cookies without `Secure` in the Compose stack | Info | Observed; documented local-only behaviour |

### F-01 — Whitespace-only text made of Unicode spaces is accepted

- **Files:** `backend/src/main/java/com/matriz/shared/Text.java` (`trim`
  uses `String.strip()`), used by `TaskCreateRequest`, `ProjectCreateRequest`,
  `CommentCreateRequest` and the PATCH rules (`TaskRules`, `ProjectRules`).
  Already recorded in `docs/test-plan.md` §9.4 (Ex2).
- **Evidence (facts):**
  - `TaskTitleRuleTests` TV-07: the two tests fail when enabled (they are
    `@Disabled` with reason F-01).
  - Live, as Ana on `Website Redesign` (§2 C3):

    | Request | Body | Status |
    |---------|------|--------|
    | `POST /projects/{P}/tasks` | `title` = 4 × U+00A0 | `201`, title stored as 4 × U+00A0 |
    | `POST /projects/{P}/tasks` | `title` = U+202F U+2007 U+FEFF U+00A0 | `201` |
    | `POST /projects/{P}/tasks` | `title` = 3 × U+0020 (control) | `400 VALIDATION_ERROR` |
    | `PATCH /tasks/{T}` | `title` and `description` = 4 × U+00A0 | `200` |
    | `POST /projects` | `name` and `description` = 4 × U+00A0 | `201` |
    | `PATCH /projects/{id}` | `name` = U+202F U+2007 U+FEFF U+00A0 | `200` |
    | `POST /tasks/{T}/comments` | `body` = 4 × U+00A0 | `201` |
    | `POST /tasks/{T}/comments` | `body` = 3 × U+0020 (control) | `400 VALIDATION_ERROR` |

  - Semgrep rule `matriz-text-trim-misses-unicode-spaces` flags
    `Text.java:13`.
- **Cause:** Java's `strip()` and `isBlank()` follow
  `Character.isWhitespace`, which excludes the no-break spaces U+00A0,
  U+2007, U+202F (and U+FEFF is not a space at all). The contract
  (`pattern: '\S'`, ECMAScript) and the UI (`String.prototype.trim`) treat
  them as whitespace. `@NotBlank` on the comment body has the same gap. The
  database checks use `btrim`, which only removes ASCII spaces.
- **Impact:** projects, tasks and comments that look empty in the UI;
  breaks FR3/FR4 "whitespace-only rejected" and the contract; the UI and the
  API disagree. Not exploitable for access; data quality only.
- **Proposed fix:** make `Text.trim` remove leading and trailing
  characters that are whitespace for the contract, i.e.
  `Character.isWhitespace` **or** `Character.isSpaceChar` (Unicode `Z*`
  categories, which include U+00A0, U+2007, U+202F) **or** U+FEFF, matching
  JavaScript `trim()`. No contract or database change. The comment body is
  trimmed with `Text.trim` in the `CommentCreateRequest` constructor before
  `@NotBlank` is validated, so the same fix covers it.
- **Proposed regression tests:** re-enable the two TV-07 tests in
  `TaskTitleRuleTests` (`@Disabled` removed); add `Text` unit tests for
  U+00A0, U+2007, U+202F, U+FEFF and mixed spaces around text (kept inside
  text); add API tests (contract-checked) for a project name, a task title
  on PATCH and a comment body made of these characters → `400
  VALIDATION_ERROR` with the field name.
- **Fix applied (approved):** `Text.trim` removes leading and trailing code
  points that are `Character.isWhitespace`, `Character.isSpaceChar` or
  U+FEFF; spaces inside text are kept. Optional fields made only of these
  characters (descriptions) become empty and are stored as `null`, as with
  ASCII spaces. Regression tests: the two TV-07 tests in
  `TaskTitleRuleTests` re-enabled; `shared/TextTests` (U+00A0, U+2007,
  U+202F, U+FEFF, U+3000, U+2028, U+2029, ASCII; inner spaces and U+200B
  kept); `ProjectApiTests.unicodeWhitespaceOnlyNameIsRejected`,
  `TaskApiTests.unicodeWhitespaceOnlyTitleIsRejected` and
  `CommentApiTests.unicodeWhitespaceOnlyBodyIsRejected` (create and PATCH,
  contract-checked `400 VALIDATION_ERROR` with the field; surrounding Unicode
  spaces trimmed on valid values). With the old `Text.java` all of these
  fail (`Status expected:<400> but was:<201>`); with the fix they pass. The
  Semgrep rule `matriz-text-trim-misses-unicode-spaces` no longer reports
  `Text.java`.

### F-02 — Sign-in rate limit bypassed with a forged `X-Forwarded-For` behind nginx

- **Files:** `docker-compose.yml` (`SERVER_FORWARD_HEADERS_STRATEGY:
  native`, added in `82adc04`), `frontend/nginx.conf`
  (`proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;`),
  `backend/src/main/java/com/matriz/identity/SignInRateLimiter.java` (keys
  on `request.getRemoteAddr()`).
- **Evidence (facts):** on the isolated stack, through the SPA port, with an
  unknown e-mail (so no demo account is locked):

  | Step | Requests | Statuses |
  |------|----------|----------|
  | A | 25 × `POST /auth/sign-in`, no `X-Forwarded-For` | 11 × `401`, then 14 × `429` (the limit of 20/min also counted the earlier sign-ins of the IDOR run) |
  | B | 10 × `POST /auth/sign-in`, `X-Forwarded-For: 203.0.113.<n>` different each time | 10 × `401` — no `429` |
  | C | 3 × `POST /auth/sign-in`, no header again | 3 × `429` (the real bucket is still full) |

- **Cause:** nginx appends the client's own `X-Forwarded-For` value instead
  of replacing it. With `native`, Tomcat's `RemoteIpValve` trusts private
  addresses as proxies; the nginx container and the Docker gateway are
  private, so the left-most, client-supplied address becomes the remote
  address the rate limiter uses. This also contradicts the Increment 3 note
  in `implementation-plan.md` ("`X-Forwarded-For` is not trusted").
- **Impact:** the 20 attempts/min/IP limit (ADR-0009, R27) does not hold
  for the Compose stack: one client can make unlimited sign-in attempts
  (password spraying across accounts). The per-account lockout (5 failures →
  15 min) still limits guessing one account. The Compose stack is
  local-only (ports bound to `127.0.0.1`), so the practical exposure today
  is low, but the stack is the documented way to run the product.
- **Proposed fix (recommended):** in `frontend/nginx.conf` replace the
  header instead of appending: `proxy_set_header X-Forwarded-For
  $remote_addr;`. The backend then sees the real peer of nginx, which the
  client cannot forge, and each client keeps its own bucket. Correct the
  Increment 3 note in `implementation-plan.md` and the comment in
  `docker-compose.yml`. Alternative: drop `SERVER_FORWARD_HEADERS_STRATEGY`
  (all browsers share one bucket, as behind the Vite proxy).
- **Proposed regression test:** a project Semgrep rule
  (`.semgrep.yml`) that fails on `$proxy_add_x_forwarded_for` in
  `frontend/nginx.conf`, plus the live check above (steps A–C) re-run and
  recorded: with the fix, step B must return `429`. An automated E2E check is
  not proposed: exhausting the limit would also block the other E2E
  sign-ins from the same address for a minute.
- **Fix applied (approved):** `frontend/nginx.conf` sets
  `proxy_set_header X-Forwarded-For $remote_addr;` (replace, not append);
  comments in `nginx.conf` and `docker-compose.yml` and the Increment 3 note
  in `implementation-plan.md` explain the trust chain. Regression: Semgrep
  rule `matriz-nginx-forwarded-for-appends-client-value` (`.semgrep.yml`,
  severity `ERROR`); run on the unfixed file it reported
  `frontend/nginx.conf` (exit 1 with `--error`), and it reports nothing
  after the fix. Live re-check on a new isolated stack (`matriz-ex5`, images
  built from the fixed tree, fresh database), same unknown e-mail:

  | Step | Requests | Statuses after the fix |
  |------|----------|------------------------|
  | A | 25 × `POST /auth/sign-in`, no `X-Forwarded-For` | 20 × `401`, then 5 × `429` |
  | B | 10 × `POST /auth/sign-in`, `X-Forwarded-For: 203.0.113.<n>` different each time | 10 × `429` — the forged header no longer gives a new bucket |
  | C | 3 × `POST /auth/sign-in`, no header again | 3 × `429`; one more with `X-Forwarded-For: 198.51.100.7` → `429`, `Retry-After: 59` |

### F-03 — Search length limits count UTF-16 units, not code points

- **Files:** `backend/src/main/java/com/matriz/projects/ProjectController.java`
  lines 52 and 118 (`term.length()`).
- **Evidence:** code reading. Increment 9 (`1053e46`) moved the other length
  rules to code points (`Text.length`) but not the `search` parameters of
  `GET /projects` (max 100) and `GET /projects/{id}/member-candidates`
  (min 2, max 100).
- **Impact:** a one-emoji member search passes the minimum of 2 that the
  contract (`minLength: 2`, code points) rejects; 51–100 emoji are rejected
  although valid. No security or data impact.
- **Proposed fix:** use `Text.length(term)`. **Proposed regression test:**
  API tests for a 1-emoji candidate search (`400`) and a 100-emoji project
  search (`200`). Low priority.
- **Decision:** not approved for this exercise (project owner, 2026-11-12):
  deferred and accepted as a known low-severity deviation; no code change.

### S-01 — SPA responses from nginx have no security headers

- **Evidence:** `GET /` on the SPA port returns `Server: nginx/1.27.5` and
  no `Content-Security-Policy`, `X-Frame-Options` or
  `X-Content-Type-Options`. API responses do have `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff` and `Cache-Control: no-store` (Spring
  Security).
- **Impact (hypothesis):** clickjacking of the SPA pages and less defence in
  depth against XSS. No XSS sink was found (no `dangerouslySetInnerHTML`,
  `innerHTML` or `eval` in `frontend/src`; React escapes text), and the
  production deployment is not defined in release 1 (`constraints.md`).
- **Proposal:** not prioritized; when the deployment target is decided, add
  `server_tokens off;` and the headers in nginx (CSP to be designed with
  the Vite build). Needs a decision, not a fix now.

### S-02 — Any user can list all active users' names and e-mails

- **Evidence:** any signed-in user may create a project (R1), becomes its
  Manager and can then search member candidates (R9) across all active
  users. The response contains only id, name and e-mail.
- **Impact:** user enumeration inside the organisation. This follows the
  documented rules (internal tool, all users are colleagues); no action
  proposed.

### S-03 — Cookies without `Secure` in the Compose stack

- **Evidence:** `matriz_session` is `HttpOnly; SameSite=Lax` and
  `XSRF-TOKEN` is `SameSite=Lax`, both without `Secure`, because the stack
  runs the `local` profile over plain HTTP. This is documented
  (`README`, `implementation-plan.md` §7) and `Secure` is set outside the
  local profile. No action proposed.

### Code review notes without a finding

- `CurrentUserFilter` (`1053e46`) re-reads the user on every request:
  deactivated users lose the session, changed roles apply at once; one
  extra primary-key query per authenticated request (acceptable for the
  expected load). Covered by `AuthenticationTests`.
- `Text.length`/`textLength` (`1053e46`) count code points on both sides;
  consistent with the contract and PostgreSQL `char_length`.
- `Timestamps.now` and the removal of the unused `ProjectAccess.can`
  (`927af1e`) are behaviour-preserving.
- Validation runs before the existence/authorization check, so an outsider
  sending an invalid body gets `400` instead of `404`; the `400` does not
  depend on the project, so it reveals nothing (by design, ADR-0010).
- Missing tests in the scope: no test for Unicode spaces on project names,
  descriptions and comments (F-01); no automated check of the forwarded-IP
  configuration (F-02); the E2E test (Ex4) covers only the happy path,
  authorization stays at API level (§4.3).

## 4. Security review of access to projects and tasks

### 4.1 Code paths

Every project-scoped operation takes the identity from the session
(`@AuthenticationPrincipal AuthenticatedUser`) and starts with
`ProjectAccessPolicy` before reading or changing data:

| Area | Entry point | Check |
|------|-------------|-------|
| Project read, update, archive, unarchive | `ProjectService` | `policy.require(projectId, user, action)` → `404` if not visible, `403` if not allowed, `409 PROJECT_ARCHIVED` for writes on archived projects |
| Members, member candidates | `ProjectService` | `policy.require(...)` / `policy.access(...)` + Manager or Administrator |
| Project list, search, dashboard | `ProjectQueries` | SQL filtered by membership of the session user (or Administrator) |
| Tasks: list, create | `TaskService` | `policy.require(projectId, ...)` |
| Tasks: read, update, delete, assign, unassign | `TaskService` | project resolved from the task (`queries.projectOf(taskId)`), then `policy.requireTask(...)`, which also checks assignment for Members |
| Comments: list, add | `CommentService` | project resolved through `TaskLookup`, then `policy.requireTask(...)` |

No endpoint takes the user, owner or role from the request body for
authorization. Semgrep rules `matriz-endpoint-without-session-user` and
`matriz-service-without-access-policy` found no exception besides the
public authentication endpoints and a private helper called after the check
(§6.2).

### 4.2 Manual IDOR check (two demo accounts)

Setup: `docker compose -p matriz-ex5 -f docker-compose.yml -f
frontend/e2e/docker-compose.e2e.yml up -d --build --wait` with the
fictitious values of `.env.example` and `MATRIZ_E2E_WEB_PORT=5181`; one
`curl` cookie jar per user; `GET /api/v1/auth/csrf`, then `POST
/auth/sign-in` with the `X-XSRF-TOKEN` header, then a fresh CSRF token.
Real identifiers were read as Ana: `{P}` = *Website Redesign*, `{T}` = an
unassigned task, `{TR}` = a task assigned to Rui. Marta is Manager of other
projects and not a member of *Website Redesign*; Rui is a Member of it.
The stack was removed afterwards (`down --volumes`).

| Caller | Request (base `/api/v1`) | Expected (§4 of the plan) | Status |
|--------|--------------------------|---------------------------|--------|
| Marta | `GET /projects/{P}` | 404 | 404 |
| Marta | `PATCH /projects/{P}` (rename) | 404 | 404 |
| Marta | `POST /projects/{P}/archive` | 404 | 404 |
| Marta | `POST /projects/{P}/unarchive` | 404 | 404 |
| Marta | `GET /projects/{P}/members` | 404 | 404 |
| Marta | `POST /projects/{P}/members` (add herself as Manager) | 404 | 404 |
| Marta | `PATCH /projects/{P}/members/{rui}` (to Manager) | 404 | 404 |
| Marta | `DELETE /projects/{P}/members/{rui}` | 404 | 404 |
| Marta | `GET /projects/{P}/member-candidates?search=ma` | 404 | 404 |
| Marta | `GET /projects/{P}/tasks` | 404 | 404 |
| Marta | `POST /projects/{P}/tasks` | 404 | 404 |
| Marta | `GET /tasks/{T}` | 404 | 404 |
| Marta | `PATCH /tasks/{T}` (with the real `version`) | 404 | 404 |
| Marta | `DELETE /tasks/{T}` | 404 | 404 |
| Marta | `POST /tasks/{T}/assignees` (herself) | 404 | 404 |
| Marta | `DELETE /tasks/{TR}/assignees/{rui}` | 404 | 404 |
| Marta | `GET /tasks/{T}/comments` | 404 | 404 |
| Marta | `POST /tasks/{T}/comments` | 404 | 404 |
| Marta | `GET /projects?search=Website` | 200, no item | 200, `totalItems: 0` |
| Marta | `GET /projects` | only her projects | 200, `["Mobile App Launch"]` |
| Marta | `GET /dashboard` | only her projects | 200, only *Mobile App Launch* |
| Rui | `GET /projects/{P}` | 200 | 200 |
| Rui | `PATCH /projects/{P}` | 403 | 403 |
| Rui | `POST /projects/{P}/archive` | 403 | 403 |
| Rui | `GET /projects/{P}/member-candidates?search=ma` | 403 | 403 |
| Rui | `POST /projects/{P}/members` (add Marta) | 403 | 403 |
| Rui | `PATCH /projects/{P}/members/{rui}` (himself to Manager) | 403 | 403 |
| Rui | `DELETE /projects/{P}/members/{ana}` | 403 | 403 |
| Rui | `POST /projects/{P}/tasks` | 403 | 403 |
| Rui | `PATCH /tasks/{T}` (not assigned) | 403 | 403 |
| Rui | `DELETE /tasks/{T}` / `DELETE /tasks/{TR}` | 403 | 403 / 403 |
| Rui | `POST /tasks/{T}/assignees` (himself) | 403 | 403 |
| Rui | `GET /tasks/{T}/comments` | 200 | 200 |
| Rui | `PATCH /tasks/{TR}` (assigned; positive control) | 200 | 200 |
| (no session) | `GET /projects/{P}`, `GET /tasks/{T}` | 401 | 401 / 401 |
| Ana (no `X-XSRF-TOKEN`) | `PATCH /projects/{P}` | 403 (CSRF) | 403 |
| Vitor (Administrator) | `GET /projects/{P}` | 200 | 200 |

State after the attempts (read as Ana): project name *Website Redesign*,
status `ACTIVE`; members unchanged (Ana Manager/owner, Rui Member); task
`{T}` unchanged (`version` 1, no assignee); Rui still assigned to `{TR}`; no
comment and no task created by Marta or Rui. The `404` bodies contain only
`type`, `title`, `status`, `code` and `instance` (the requested path), e.g.
`{"instance":"/api/v1/tasks/…","status":404,"title":"Not found","type":"/problems/not-found","code":"NOT_FOUND"}` — the same as for a
nonexistent id, so a hidden project cannot be told apart from a missing one.

**Result:** no IDOR or broken access control was found; the behaviour
matches R1–R15 of `implementation-plan.md` §4.

### 4.3 Existing automated authorization tests

| Test | Covers |
|------|--------|
| `ApiAuthorizationTests` (3) | Outsider `404` on every operation and Member `403` on manager actions, state unchanged; identity and ownership cannot be supplied by the client |
| `ProjectAccessTests` (34) | Unit test of the access matrix (`ProjectAccess`), action by action: Manager/Administrator, Member (view, comment, assigned tasks), archived projects, problem codes |
| `ProjectAccessPolicyTests` (8) | The policy against the database: visibility (outsider `404`), roles, tasks of other projects `404`, Administrator, archived, anonymous |
| `TaskApiTests.permissionsFollowTheAccessMatrix` | Task operations per role and assignment |
| `AssigneeApiTests`, `CommentApiTests` | Assign/unassign and comments per role, outsider `404` |
| `TaskCreateReadIntegrationTests` (Ex3) | Create/read flow: anonymous `401`, Member, outsider `404` |
| `AuthenticationTests` | Sign-in, CSRF, lockout, `429` (direct remote address only) |

The manual check found nothing these tests miss for access control. The
gap they share is F-02: MockMvc does not run Tomcat's `RemoteIpValve` or
nginx, so the forwarded-address path is untested.

## 5. Data exposure

- Problem details (ADR-0010) for `400` (malformed JSON, invalid UUID,
  unknown field), `401` (wrong password: generic "Invalid email or
  password"), `403` and `404` contain no exception names, stack traces, SQL
  or internal identifiers.
- Responses contain only the fields of the contract; users are exposed as
  id, name and e-mail; password hashes never leave the `identity` module.
- Actuator: only `health` is exposed (`/actuator/env` and `/actuator` →
  `401` on the backend). On the SPA port, unknown paths such as
  `/actuator/env` return the SPA's `index.html` (nginx fallback), not
  backend data.
- Logging: only the lockout (user id), the demo seed and unexpected errors
  are logged; no passwords, tokens or request bodies (code reading of all
  `log.*` calls).

## 6. Static analysis

### 6.1 oxlint (configured, `frontend/`)

| Run | Result |
|-----|--------|
| `npm run lint` (project configuration, oxlint 1.90.0) | 0 warnings, 0 errors |
| Extended: `npx oxlint -D correctness -D suspicious -W perf -A react/react-in-jsx-scope` | 4 diagnostics (2 errors, 2 warnings), none security-related |

| Rule | Location | Triage |
|------|----------|--------|
| `react(no-array-index-key)` (warning) | `components/Breadcrumbs.tsx:15` | False positive in effect: static list that never reorders |
| `react(memo-dependencies)` (error, "extra dependencies") | `auth/AuthProvider.tsx:56` | False positive: `user`, `isError`, `sessionExpired`, `signIn` and `signOut` are all used in the memoized value |
| `react(no-unstable-nested-components)` (error) | `pages/TaskDetailPage.tsx:157` | False positive: `errorAction` is a render-function prop returning elements, not a component type |
| `react(no-object-type-as-default-prop)` (warning, `initial = {}`) | `components/TaskForm.tsx:59` | Suspicion of extra re-renders only; no functional or security impact; no action |

### 6.2 Semgrep (added in this review)

Semgrep was added permanently: `.semgrep.yml` (project rules),
`.semgrepignore` and `scripts/semgrep.sh`, which runs the pinned Docker
image `semgrep/semgrep:1.184.0` with metrics off and the registry rulesets
`p/default`, `p/java`, `p/typescript`, `p/react`, `p/secrets`,
`p/dockerfile`, `p/docker-compose` and `p/nginx` (command in the README).
The registry rules are downloaded at run time, so the run needs network
access; behind a TLS-inspecting proxy the script takes the proxy's root
certificate from `MATRIZ_EXTRA_CA_CERT`, as the image builds do.

Project rules:

| Rule | Looks for |
|------|-----------|
| `matriz-text-trim-misses-unicode-spaces` | `strip()`/`trim()`/`isBlank()` on input (F-01) |
| `matriz-endpoint-without-session-user` | Mapped endpoints without `@AuthenticationPrincipal AuthenticatedUser` |
| `matriz-service-without-access-policy` | Service methods with a user and a resource id that do not call the policy |
| `matriz-jdbc-sql-built-dynamically` | SQL text built by concatenation or `formatted` (injection) |
| `matriz-nginx-forwarded-for-appends-client-value` | `$proxy_add_x_forwarded_for` in `frontend/nginx.conf` (F-02 regression guard, added with the fix) |

Run on 2026-11-12: 408 rules, 178 files tracked by git, 0 errors,
**20 findings**.

| Rule | Location | Triage |
|------|----------|--------|
| `matriz-text-trim-misses-unicode-spaces` | `shared/Text.java:13` | **Confirmed** — F-01 |
| same | `identity/SignInService.java:51` (`email.strip()`) | False positive: a forged e-mail with U+00A0 matches no account → generic `401` |
| same | `tasks/TaskController.java:122` (filter values) | False positive: a value with U+00A0 is not an enum name → `400` |
| `matriz-endpoint-without-session-user` | `identity/AuthController.java:63, 69, 92` (`csrf`, `sign-in`, `sign-out`) | False positive: public authentication endpoints by design |
| `matriz-service-without-access-policy` | `projects/ProjectService.java:218` (`view`) | False positive: private helper called after `policy.require`; its query also filters by the user |
| `matriz-jdbc-sql-built-dynamically` | `CommentQueries` (2), `UserDirectory` (1), `ProjectQueries` (4), `TaskQueries` (3), `TaskStatistics` (2) | False positives: only constant fragments are concatenated (column lists, `FROM`, filter clauses, `TaskSort.orderBy()` from enum constants, the `NOT IN (:excluded)` clause); every request value is a bound parameter |
| `request-host-used` (`p/nginx`) | `frontend/nginx.conf:9` (`Host $http_host`) | False positive: the backend builds no absolute URLs from `Host` (problem `instance` is the path only; no redirects or e-mail links) |

The registry rulesets for Java, TypeScript, React, secrets, Dockerfiles and
Compose returned no findings. Semgrep did not detect F-02 (no rule covers
`$proxy_add_x_forwarded_for` with a trusting backend); it was found by
review and confirmed live.

## 7. Secrets

- Working tree: the only credentials are the fictitious values in
  `.env.example` (`change-me-local-only`, demo password `MatrizDemo#2026`,
  both documented as fictitious) and the fake API password in frontend
  tests. Semgrep `p/secrets` and `p/default` found nothing.
- History: `git log -p --all` (128 commits) searched for private keys,
  tokens, API keys, `password=`/`secret=` assignments and JDBC URLs with
  credentials: same fictitious values only. `.env` was never committed
  (`.gitignore`: `.env`, `.env.*`, `!.env.example`); `.idea/` is ignored.

## 8. Decisions and status

| ID | Proposed action | Priority | Decision | Status |
|----|-----------------|----------|----------|--------|
| F-01 | Fix `Text.trim`; re-enable TV-07; add `Text` and API tests | 1 | Approved by the project owner (2026-11-12) | **Fixed** — regression tests listed in F-01 |
| F-02 | Replace `X-Forwarded-For` in nginx; Semgrep rule + live re-check; correct the docs | 2 | Approved by the project owner (2026-11-12) | **Fixed** — Semgrep rule and live re-check in F-02 |
| F-03 | Use `Text.length` for `search` | 3 (optional) | Not approved (project owner, 2026-11-12) | Deferred — accepted low-severity deviation |
| S-01 | Security headers in nginx when the deployment is defined | — | Not approved now; needs a deployment decision | Deferred — accepted for the local-only stack |
| S-02, S-03 | None (documented behaviour) | — | Accepted as documented behaviour | Closed |

Verification of the fixes (2026-11-12, C10):

| Command | Result |
|---------|--------|
| `cd backend && ./mvnw verify` (after F-01) | BUILD SUCCESS; 179 test cases in the Surefire reports, 0 failed, 0 skipped (was 165 with 2 skipped) |
| Same tests run against the old `Text.java` | F-01 regression tests fail (`Status expected:<400> but was:<201>`, TV-07, `TextTests`) — they detect the defect |
| `cd frontend && npm test` | 5 files, 61 tests passed |
| `cd frontend && npm run lint` / `npm run build` / `npm run api:check` | No findings / build succeeded / types match `openapi.yaml` (contract unchanged, Redocly not needed) |
| `cd frontend && npm run test:e2e` | 1 passed (images built from the fixed tree; stack removed) |
| `scripts/semgrep.sh` | 409 rules, 179 files, 0 errors, 19 findings: all previously triaged false positives; the F-01 finding on `Text.java` and the new F-02 rule no longer report |
| IDOR script (C2) on the fixed stack | Same statuses as in §4.2 for every request |
