# Matriz 1.0.0 — Release notes

Version **1.0.0** (2026-11-18) is the release candidate of release 1 of
Matriz, a project and task management application for teams. This document
describes what it contains, how to run it, how it was verified and what is
known to be missing. The full list of changes is in
[CHANGELOG.md](../CHANGELOG.md).

## Versioning

- The application follows [Semantic Versioning](https://semver.org/):
  backend (`backend/pom.xml`) and frontend (`frontend/package.json`) are both
  `1.0.0`. Later fixes become patch versions (1.0.x) and backward-compatible
  features minor versions (1.x.0).
- The API contract `openapi.yaml` keeps its own version, **0.2.3**. It
  versions the HTTP contract independently of the application
  ([ADR-0016](adr/0016-contract-first-api-and-generated-client.md)) and has
  not changed since Increment 7, so 1.0.0 ships contract 0.2.3 unchanged; it
  changes only when the contract changes. The base path stays `/api/v1`.
- Tagging: no Git tag or GitHub release has been created. Creating the tag
  `v1.0.0` on the release commit (and, if wanted, a GitHub release with these
  notes) is a step for the release manager. The version links at the end of
  `CHANGELOG.md` resolve once the tag exists.

## Highlights

- Sign-in with server-side sessions, CSRF protection, Argon2id password
  hashes, account lockout and a per-IP sign-in rate limit.
- Projects with search, archive/unarchive and members (Project Manager /
  Member), all authorized on the server; projects a user cannot see answer
  `404`.
- Tasks with status (To do, In progress, Done), priority, multiple assignees,
  filters and sorting, and optimistic concurrency (`409` instead of silently
  overwriting another user's edit).
- Comments on tasks and a dashboard of progress per project.
- Runs as three containers with one command (`docker compose up -d --build
  --wait`), with healthchecks, isolated networks and a persistent volume.
- Every push is checked by GitHub Actions: backend and frontend tests, lint,
  build, contract lint, image build and a browser end-to-end test.

## Features

| Area | In 1.0.0 |
|------|----------|
| Accounts | Demo accounts from the seed (`MATRIZ_SEED_DEMO=true`): one administrator and four users; sign-in, sign-out, current user; sessions end after 30 min idle or 8 h |
| Projects | Create, edit, search by name, paging, archive and unarchive (archived = read-only), member list, add/remove members, change role, member candidates |
| Tasks | Create, list (filters: status, priority, assignee; sort; paging), read, edit, delete permanently, assign and unassign members |
| Comments | List and add comments on a task (immutable) |
| Dashboard | Accessible active projects with task counts per status and % Done |
| API | 25 operations under `/api/v1`, OpenAPI 3.1 contract `openapi.yaml`, errors as `application/problem+json` |

Step-by-step usage is in the [user guide](user-guide.md).

## How to run

Only Docker with Compose and a `.env` are needed:

```sh
cp .env.example .env                    # fictitious values
docker compose up -d --build --wait     # db, backend, frontend; returns when all are healthy
docker compose ps
```

Open http://localhost:5173 and sign in as `ana@matriz.test` with the demo
password from `.env.example` (`MATRIZ_DEMO_PASSWORD`). Stop with
`docker compose down` (data kept) or `docker compose down -v` (data
deleted). Development without containers for the API and SPA, the tests and
all configuration variables are described in the [README](../README.md).

## Verification summary

Results from real runs only; details and the commands are in
[delivery.md](delivery.md) §5, [test-plan.md](test-plan.md) and
[verification.md](verification.md).

CI on GitHub (workflow `CI`, `push` events):

| Run | Commit | Result |
|-----|--------|--------|
| [44302301240](https://github.com/hugosousa1231830/matriz/actions/runs/44302301240) | `614b9ca` (workflow added) | Failure: backend test-order dependency and frontend test timeout (fixed) |
| [44302777727](https://github.com/hugosousa1231830/matriz/actions/runs/44302777727) | `f3e893a` | Failure: `docker compose build` refused the `/dev/null` build secret under Compose Bake (fixed) |
| [44303449662](https://github.com/hugosousa1231830/matriz/actions/runs/44303449662) | `e4a766f` | Success: all four jobs; E2E 1 passed |
| [44303964829](https://github.com/hugosousa1231830/matriz/actions/runs/44303964829) | `bdcf940` (short-lived branch) | Controlled failure: a deliberately wrong assertion failed the backend job and the Docker job was skipped |
| [44304232407](https://github.com/hugosousa1231830/matriz/actions/runs/44304232407) | `0fa9108` (same branch) | Success after restoring the assertion |
| [44304954585](https://github.com/hugosousa1231830/matriz/actions/runs/44304954585) | `22cdc66` | Success: backend, frontend, contract and docker (Compose build + E2E) jobs, 2 min 51 s |

The runs on the 1.0.0 documentation commits are recorded in
[delivery.md](delivery.md) §5 (Stage 5).

Local checks with version 1.0.0 (2026-11-18):

| Check | Result |
|-------|--------|
| `cd backend && ./mvnw verify` | BUILD SUCCESS, 179 tests, 0 failures, 0 errors; jar `matriz-backend-1.0.0.jar` |
| `cd frontend && npm test` / `npm run lint` / `npm run build` / `npm run api:check` | 5 files, 61 tests passed / exit 0 / exit 0 / exit 0 |
| `npx --yes @redocly/cli@2.79.0 lint openapi.yaml` | Valid (1 problem explicitly ignored in `redocly.yaml`) |
| `docker compose config -q`; `docker compose build` | Valid; both images built; the API jar in the image reports `Implementation-Version: 1.0.0` |
| `docker compose up -d --wait`; `docker compose ps`; `curl` health, SPA and API through nginx; `docker compose down` | All three services `healthy`; log "Starting MatrizApplication v1.0.0"; `/actuator/health` `UP` (db `UP`); SPA `200`; anonymous `GET /api/v1/auth/me` `401`; stack stopped, volume kept |
| `npx --yes @redocly/cli@2.79.0 build-docs openapi.yaml -o /tmp/matriz-api-docs.html` | HTML page "Matriz API" (322 KiB) written outside the repository |

Earlier recorded results: release 1 verification cases 01–09
([verification.md](verification.md)); unit, integration and end-to-end tests
of task management and permissions ([test-plan.md](test-plan.md) §9–§11);
IDOR check with the demo accounts and Semgrep triage
([quality-security-review.md](quality-security-review.md)); images run with
`docker run` and the Compose stack checked end to end, including
persistence across `down`/`up` ([delivery.md](delivery.md) §5, Stages 2–3).

Not covered: load and performance tests (no p95 measurement), browsers
other than Chromium, end-to-end flows other than creating a task, a
production deployment.

## Known limitations

- Accounts come only from the demo seed: no registration, password reset or
  account administration UI.
- Deferred features: deadlines and overdue flagging, change history,
  notifications, Viewer role.
- Projects are archived, never deleted; tasks are deleted permanently.
- No real-time updates: screens refresh every 60 seconds and on navigation.
- Single backend instance: the sign-in rate limit is kept in memory.
- Local stack only: there is no deployment or hosting configuration, no TLS,
  and the `local` profile used by Compose sends cookies without `Secure`.
- Deferred review findings ([quality-security-review.md](quality-security-review.md) §8):
  F-03, search length limits count UTF-16 units instead of code points (low);
  S-01, nginx sends no security headers and shows its version (low, to be
  handled when a deployment is defined).
- The SPA container uses the default nginx user setup (master process as
  root); the API container runs as a non-root user.
- Semgrep is a local check with manual triage, not part of CI; the
  `p/docker-compose` ruleset does not match `compose.yaml` (it needs an
  obsolete `version:` key), so the Compose file is reviewed by hand.
- With Compose 5.1 (Buildx Bake), a `MATRIZ_EXTRA_CA_CERT` outside the
  repository triggers a file-access prompt during `docker compose build`;
  CI sets `BUILDX_BAKE_ENTITLEMENTS_FS=0` for its `/dev/null` secret.

## Upgrade and migration notes

- First version: there is no earlier release to upgrade from.
- Database: Flyway migrations `V1__schema.sql` and `V2__spring_session.sql`
  are applied automatically when the backend starts; no manual step. A
  database created by earlier development builds of release 1 is already at
  V2 and is only validated. Future schema changes will be new migrations
  (`V3__…`), never edits of V1–V2.
- Configuration: `.env.example` lists every variable; no variable was
  renamed for 1.0.0. The Compose file is `compose.yaml` (formerly
  `docker-compose.yml`); `docker compose` finds it without `-f`.
- Data: the named volume `matriz-db-data` is kept across versions; the demo
  seed runs only when the `users` table is empty.
