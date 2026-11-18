# Changelog

All notable changes to Matriz are documented in this file.

The format is based on [Keep a Changelog 1.1.0](https://keepachangelog.com/en/1.1.0/),
and the project follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).
The version applies to the application (backend `pom.xml` and frontend
`package.json`); the API contract `openapi.yaml` carries its own version
(currently 0.2.3), which changes only when the contract changes.

## [Unreleased]

Fixes go into patch versions (1.0.x) and backward-compatible features into
minor versions (1.x.0).

### Changed

- Documentation from the final validation (`docs/delivery.md` §8): README
  prerequisites (Node 22.13+, Compose 2.24.4+, SPA port), exporting
  `MATRIZ_EXTRA_CA_CERT` for `scripts/semgrep.sh`, `.env` values for the
  backend container and the `502` workaround after recreating the backend
  alone; outdated "not pushed" statements marked as historical.

## [1.0.0] - 2026-11-18

First version: release 1 of Matriz (increments 1 to 9 of
[docs/implementation-plan.md](docs/implementation-plan.md)), its test and
review work and its preparation for delivery. Release notes:
[docs/release-notes-1.0.0.md](docs/release-notes-1.0.0.md).

### Added

- Specification and architecture: requirements, user stories, constraints,
  architecture (modular monolith), data model, ADR-0001 to ADR-0016 and the
  OpenAPI 3.1 contract (`openapi.yaml`, contract versions 0.2.0 to 0.2.3).
- Backend skeleton (Java 21, Spring Boot, Maven wrapper), PostgreSQL 16 in
  Docker Compose, Flyway migrations `V1__schema.sql` and
  `V2__spring_session.sql`, idempotent demo data seed, `.env.example`.
- Frontend skeleton (React, Vite, TypeScript, TanStack Query) with the API
  client generated from `openapi.yaml` and a contract drift check
  (`npm run api:check`).
- Authentication: sign-in and sign-out with server-side sessions stored in
  PostgreSQL (idle 30 min, absolute 8 h), CSRF protection, Argon2id password
  hashes, account lockout after 5 failures and a per-IP sign-in rate limit;
  `application/problem+json` errors; frontend sign-in flow.
- Projects: create, edit, search, paging, archive and unarchive (archived
  projects are read-only), members with Project Manager / Member roles and
  member candidates; server-side project access policy (hidden projects
  answer `404`).
- Dashboard (`GET /dashboard`): accessible active projects with task counts
  per status and % Done; landing page of the SPA.
- Tasks: create, list with status, priority and assignee filters, sorting
  and paging, read, edit with optimistic concurrency (`409` on a stale
  version), permanent delete, status flow To do / In progress / Done,
  priorities, multiple assignees.
- Comments on tasks (list and add; immutable).
- UI: layout with navigation and sign-out, shared form, feedback, dialog,
  pagination and badge components, confirmations for archive, task delete
  and member removal, 60 s refresh while visible, not-found page.
- Verification of release 1 ([docs/verification.md](docs/verification.md))
  and a step-by-step user guide ([docs/user-guide.md](docs/user-guide.md)).
- Whole stack in containers: `backend/Dockerfile` (multi-stage, non-root
  user), `frontend/Dockerfile` (SPA built and served by nginx, which proxies
  `/api/`), Docker Compose services `db`, `backend` and `frontend`.
- Test plan for task management and permissions
  ([docs/test-plan.md](docs/test-plan.md)) with unit tests of the task title
  rule, an integration test of creating and reading a task (Testcontainers)
  and a Playwright end-to-end test of creating a task through the UI
  (`npm run test:e2e`, isolated `matriz-e2e` Compose project).
- Code and security review
  ([docs/quality-security-review.md](docs/quality-security-review.md)) with
  Semgrep (`.semgrep.yml`, `scripts/semgrep.sh`).
- Delivery plan and evidence ([docs/delivery.md](docs/delivery.md)).
- Continuous integration: GitHub Actions workflow `.github/workflows/ci.yml`
  on push and pull request with jobs `backend`, `frontend`, `contract` and
  `docker` (Compose build and Playwright E2E after the other three),
  read-only permissions and no secrets; README status badge.
- Documentation: README sections on technologies and API documentation
  (Redocly `build-docs`), runtime and CI views in
  [docs/architecture.md](docs/architecture.md), this changelog and the
  release notes.

### Changed

- Deactivated users lose their session on the next request; text length
  limits count Unicode code points (Increment 9).
- `docker-compose.yml` renamed to `compose.yaml` and the E2E overlay to
  `frontend/e2e/compose.e2e.yaml`; networks `backend-net` (db, backend) and
  `frontend-net` (backend, frontend), so nginx cannot reach the database;
  healthchecks on every service and health-based start order
  (`docker compose up -d --wait`).
- Base images pinned to exact patch tags (`eclipse-temurin:21.0.13_9-jdk`/
  `-jre`, `node:22.23.5-alpine`, `nginx:1.27.5-alpine`,
  `postgres:16.16-alpine`, also used by Testcontainers); `.dockerignore`
  files exclude `.env*` and editor files.
- Redocly CLI pinned to 2.79.0 in the documented commands and in CI.
- Version set to 1.0.0 (backend was `0.1.0-SNAPSHOT`, frontend `0.1.0`).

### Fixed

- F-01: task titles and descriptions, project names and descriptions and
  comment bodies made only of Unicode space characters (for example U+00A0)
  are now treated as empty and rejected where required.
- Backend test-order dependency (`MemberApiTests` users without an Argon2id
  hash) and a frontend test timeout, both found by CI.
- `docker compose build` in CI with Compose Bake and the `/dev/null` build
  secret (`BUILDX_BAKE_ENTITLEMENTS_FS=0` on the CI Docker job).

### Security

- F-02: nginx replaces `X-Forwarded-For` with the peer address instead of
  appending the client's value, so a forged header can no longer bypass the
  sign-in rate limit in the Compose stack; Semgrep regression rule added.

[Unreleased]: https://github.com/hugosousa1231830/matriz/compare/v1.0.0...HEAD
[1.0.0]: https://github.com/hugosousa1231830/matriz/releases/tag/v1.0.0
