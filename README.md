# Matriz

Matriz is a project and task management application for teams.

## Problem

Teams often lack clear, shared visibility over the status of their projects and
tasks. It is hard to know what is being worked on, by whom, what is overdue, and
how a project is progressing overall. Matriz aims to solve this by centralizing
projects and tasks in one place with a clear view of their status.

## Main objectives

- Centralize projects and tasks in a single place.
- Make the progress and status of work visible at any time.
- Support basic collaboration around tasks.
- Keep the first version simple, usable, and deliverable.

## Planned MVP features

Release 1 (delivered) implements the items marked *(release 1)*; the others
are deferred and kept as future work — see
[docs/implementation-plan.md](docs/implementation-plan.md).

- Authentication *(release 1: seeded demo accounts, sign-in/out, lockout)* and account management.
- Project management — create, edit, search, archive/unarchive, members *(release 1)*.
- Task management — create, edit, delete, multiple assignees *(release 1)*.
- Task statuses and flow (To do, In progress, Done) *(release 1)*.
- Priorities *(release 1)* and deadlines *(deferred)*.
- Task filtering and sorting *(release 1: status, priority, assignee)*.
- Project progress dashboard *(release 1)*.
- Comments on tasks *(release 1)*.
- Overdue task flagging *(deferred)*.
- Change history *(deferred)*.
- Simple in-app notifications *(deferred)*.

Features planned for later: Viewer role, subtasks and dependencies,
attachments, email notifications, data import/export, and advanced reports.

## Documentation

| Document | Content |
|----------|---------|
| [docs/user-guide.md](docs/user-guide.md) | Step-by-step user guide: sign in, dashboard, projects, members, tasks, assignees, comments, and a guided tour with the demo accounts |
| [docs/requirements.md](docs/requirements.md) | Vision, user types, functional and non-functional requirements, MVP scope |
| [docs/user-stories.md](docs/user-stories.md) | MVP user stories, priorities, acceptance criteria, exceptional cases |
| [docs/constraints.md](docs/constraints.md) | Known constraints, assumptions, security/privacy, pending decisions, scope limits |
| [docs/implementation-plan.md](docs/implementation-plan.md) | Release 1 scope reconciliation, access rules, increments with acceptance criteria, verification and status |
| [docs/verification.md](docs/verification.md) | Release 1 verification: cases 01–09 with evidence, how to run the checks, results and limitations |
| [docs/test-plan.md](docs/test-plan.md) | Test plan for task management and permissions: scenario matrix, selected scenarios with expected results and associated tests, review of AI suggestions |
| [docs/architecture.md](docs/architecture.md) | Architecture overview (modular monolith), technology stack summary, components, assumptions, review log |
| [docs/adr/](docs/adr/README.md) | Architecture Decision Records (MADR 4.0), incl. the technology stack decision (ADR-0012) |
| [docs/database.md](docs/database.md) | Data model (incl. task assignees N:M), business rules, and Mermaid ER diagram |
| [openapi.yaml](openapi.yaml) | OpenAPI 3.1 contract for all release 1 operations (auth, dashboard, projects, members, tasks, assignees, comments); lint with `npx --yes @redocly/cli lint openapi.yaml` |
| [docs/ui/wireframe.png](docs/ui/wireframe.png) | Low-fidelity wireframe of the project dashboard (source: `docs/ui/wireframe.html`) |

## Getting started

### Prerequisites

- Java 21 (the Maven wrapper `backend/mvnw` downloads Maven itself).
- Node.js 20.19+ / 22.12+ and npm.
- Docker with Docker Compose (PostgreSQL 16 runs in a container; the backend
  tests also start PostgreSQL containers with Testcontainers).
- Free local ports: 5432 (PostgreSQL), 8080 (API) and 5173 (SPA). Ports 5432
  and 8080 can be changed in `.env`; the SPA port 5173 is fixed.
- Network access to Maven Central and the npm registry for the first build.
  If your `~/.m2/settings.xml` routes Maven through a mirror that is not
  reachable, run the wrapper with a settings file that uses Maven Central
  (`./mvnw -s <settings.xml> …`).

### Quick start (from a clean clone)

Run from the repository root; the backend and the frontend each keep a
terminal busy.

```sh
cp .env.example .env                              # 1. configuration (fictitious values)
docker compose up -d db                           # 2. PostgreSQL 16
(cd backend && ./mvnw spring-boot:run)            # 3. API: creates the schema and the demo data
(cd frontend && npm ci && npm run dev)            # 4. SPA on http://localhost:5173
```

Then open http://localhost:5173 and sign in as `ana@matriz.test` with the
demo password (`MATRIZ_DEMO_PASSWORD`, see [Demo data](#demo-data)). Each
step is detailed below. To run the whole application in containers instead,
see [Run with Docker Compose](#run-with-docker-compose).

### Repository layout

| Path | Content |
|------|---------|
| `backend/` | Spring Boot API (modular monolith, base package `com.matriz`; Maven wrapper `mvnw`) |
| `backend/src/main/java/com/matriz/` | Modules `identity` (accounts, sign-in, sessions, CSRF, lockout), `projects` (projects, members, access policy), `tasks` (tasks, assignees, counts), `comments`, `dashboard`, `demo` (demo data seed) and `shared` (errors, paging, text rules) |
| `backend/src/main/resources/db/migration/` | Flyway migrations: `V1__schema.sql` (application schema), `V2__spring_session.sql` (session tables) |
| `backend/src/test/` | JUnit 5 tests (MockMvc API tests validated against `openapi.yaml`, Testcontainers PostgreSQL, migrations, modularity) |
| `frontend/` | React + Vite + TypeScript SPA (TanStack Query) |
| `frontend/src/` | `api/` (typed client; `schema.d.ts` generated from `openapi.yaml`), `auth/`, `pages/`, `components/`, `test/` (in-memory fake API), `*.test.tsx` (Vitest + React Testing Library) |
| `openapi.yaml`, `redocly.yaml` | API contract (source of truth) and its lint configuration |
| `docs/` | Requirements, architecture, ADRs, data model, implementation plan, verification |
| `docker-compose.yml` | Local stack: PostgreSQL 16 (`db`), the API (`backend`, built from `backend/Dockerfile`) and the SPA (`frontend`, built from `frontend/Dockerfile`, served by nginx with `frontend/nginx.conf`) |
| `.env.example` | Example configuration with fictitious values |

### Configuration

All local configuration comes from environment variables, read from a `.env`
file at the repository root. Copy the example and adjust if needed:

```sh
cp .env.example .env
```

`.env` is git-ignored and must never be committed; `.env.example` only holds
fictitious values. Real environment variables take precedence over `.env`.

| Variable | Purpose |
|----------|---------|
| `MATRIZ_DB_HOST`, `MATRIZ_DB_PORT`, `MATRIZ_DB_NAME`, `MATRIZ_DB_USER`, `MATRIZ_DB_PASSWORD` | Database connection (also used by Docker Compose to create the database) |
| `MATRIZ_API_PORT` | Backend HTTP port (default 8080; the Vite proxy follows it) |
| `MATRIZ_SEED_DEMO` | `true` loads the demo data when the database has no users |
| `MATRIZ_DEMO_PASSWORD` | Password given to every demo account (required when seeding) |
| `MATRIZ_WEB_PORT` | Optional. Host port of the SPA container in Docker Compose (default 5173) |
| `MATRIZ_EXTRA_CA_CERT` | Optional. Path to a PEM file with an extra CA certificate used while building the Docker images, for networks behind a TLS-inspecting proxy |

### Database schema

There is no manual schema step. The schema is defined by the Flyway
migrations in `backend/src/main/resources/db/migration/`; the backend applies
them on every start (only versions not applied yet) and Hibernate then
validates the entities against the schema (`ddl-auto: validate`). The demo
data is inserted afterwards (see [Demo data](#demo-data)).

### Run locally

Each command in its own terminal, from the repository root:

```sh
docker compose up -d db                  # start PostgreSQL (data kept in volume matriz-db-data)
cd backend && ./mvnw spring-boot:run     # API on http://localhost:8080 (applies migrations, seeds demo data)
cd frontend && npm ci && npm run dev     # SPA on http://localhost:5173 (proxies /api to the backend)
```

Stop the backend and the SPA with `Ctrl+C` and the database with
`docker compose stop db` (data is kept). If port 8080 is taken, set
`MATRIZ_API_PORT` in `.env`; the Vite proxy and the commands below use that
port instead. If the SPA starts before the backend is ready, the dev server
logs `ECONNREFUSED` proxy errors until the API is up; they are harmless.

After sign-in the SPA opens the dashboard (`/`); projects are at
`/projects`. The SPA calls the API at `/api/v1` (proxied by Vite in
development); set `VITE_API_BASE_URL` at build time to call another address.
Screens refresh their data every 60 seconds while visible (no real-time
updates).

Check that the application is up and reaches the database:

```sh
curl http://localhost:8080/actuator/health   # "status":"UP" with component "db" UP
```

Inspect the database (no local `psql` needed):

```sh
docker compose exec db psql -U matriz -d matriz -c '\dt'
docker compose exec db psql -U matriz -d matriz -c 'SELECT email, is_admin FROM users'
```

`spring-boot:run` activates the `local` profile, which reads `.env`. Database
migrations (Flyway) run automatically on every start and only apply new
versions.

### Run with Docker Compose

The whole application can also run in containers, with no local Java or
Node.js needed (only Docker and a `.env`):

```sh
cp .env.example .env              # if not done yet
docker compose up -d --build      # build the images and start db, backend and frontend
docker compose logs -f backend    # follow the API logs (also: frontend, db; omit for all)
docker compose ps                 # status and ports
docker compose down               # stop and remove the containers (data is kept)
```

Open http://localhost:5173 (`MATRIZ_WEB_PORT`): nginx serves the production
build of the SPA and proxies `/api` to the backend container. The API is
also published on `MATRIZ_API_PORT` for `curl`. The backend container uses
the `local` profile (plain HTTP) with the values from `.env`, and connects to
the `db` service. Rebuild with `--build` after changing the code. Stop the
`mvnw`/`npm run dev` processes first, as they use the same ports.

If the image build cannot download packages because a corporate proxy
inspects TLS traffic (certificate errors), export the proxy's root
certificate to a PEM file outside the repository and set
`MATRIZ_EXTRA_CA_CERT` to its path in `.env`.

### Demo data

On start, with `MATRIZ_SEED_DEMO=true`, the backend inserts fictitious demo
data **only if the `users` table is empty**; restarts never wipe or duplicate
data. All demo accounts use the password from `MATRIZ_DEMO_PASSWORD`
(`MatrizDemo#2026` in `.env.example`), stored only as an Argon2id hash.

| Account | Role |
|---------|------|
| `vitor@matriz.test` | Administrator (no project memberships) |
| `ana@matriz.test` | Manager of *Website Redesign* |
| `rui@matriz.test` | Member of *Website Redesign* |
| `marta@matriz.test` | Manager of *Mobile App Launch* and of the archived *Legacy Intranet*; outsider to *Website Redesign* |
| `joao@matriz.test` | Regular user without projects |

To start again from an empty database: `docker compose down -v` (deletes the
volume), then start the database and backend again.

### Signing in and sessions

Open http://localhost:5173 and sign in with a demo account; any page needs a
session and redirects to the sign-in screen without one. The API uses a
server-side session (stored in PostgreSQL) referenced by the `matriz_session`
cookie (`HttpOnly`, `SameSite=Lax`):

- Sign-in always starts a new session. Sessions end after **30 minutes
  without requests** or **8 hours after sign-in**, whichever comes first, and
  immediately on sign-out or when the user is deactivated. The SPA then
  shows "Your session has expired. Please sign in again."
- Every `POST`/`PATCH`/`PUT`/`DELETE` (sign-in and sign-out included) must send
  the `X-XSRF-TOKEN` header with the value of the `XSRF-TOKEN` cookie, issued by
  `GET /api/v1/auth/csrf`; otherwise the API answers `403 CSRF_INVALID`. The
  SPA's API client does this automatically.
- Wrong credentials always get the same `401 INVALID_CREDENTIALS`. After 5
  consecutive failures the account is locked for 15 minutes; more than 20
  attempts per minute from one IP get `429` with `Retry-After`.
- Cookies carry the `Secure` flag everywhere except in the `local` profile,
  which serves plain HTTP on localhost.

Sign-in from the command line (port from `MATRIZ_API_PORT`):

```sh
curl -s -c jar -b jar http://localhost:8080/api/v1/auth/csrf
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' jar)
curl -s -c jar -b jar -H "X-XSRF-TOKEN: $TOKEN" -H 'Content-Type: application/json' \
  -d '{"email":"ana@matriz.test","password":"<MATRIZ_DEMO_PASSWORD>"}' \
  http://localhost:8080/api/v1/auth/sign-in
curl -s -b jar http://localhost:8080/api/v1/auth/me
TOKEN=$(awk '$6=="XSRF-TOKEN"{print $7}' jar)   # rotated at sign-in
curl -s -c jar -b jar -H "X-XSRF-TOKEN: $TOKEN" -X POST http://localhost:8080/api/v1/auth/sign-out
```

### Tests and checks

Docker must be running for the backend tests (they start their own
PostgreSQL container and do not touch the local database). Run each line
from the repository root:

```sh
cd backend && ./mvnw verify   # unit, migration, modularity, security/authorization, contract and smoke tests
cd frontend && npm test        # Vitest + React Testing Library
cd frontend && npm run lint    # oxlint
cd frontend && npm run build   # type-check and production build
cd frontend && npm run api:check  # regenerate the API types and fail if they differ from openapi.yaml
npx --yes @redocly/cli lint openapi.yaml
```

After changing `openapi.yaml`, run `npm run api:generate` in `frontend/` and
commit the updated `src/api/schema.d.ts`. Results of the last full run and the
manual verification are recorded in
[docs/verification.md](docs/verification.md).

## Known limitations

- Accounts come only from the demo seed: no registration, password reset or
  account administration UI.
- Deferred features: deadlines and overdue flagging, change history,
  notifications (see [docs/implementation-plan.md](docs/implementation-plan.md) §2).
- Projects are archived, never deleted; tasks are deleted permanently.
- No real-time updates: screens refresh every 60 seconds and on navigation.
- Sign-in rate limiting is kept in memory (single backend instance); the
  account lockout is stored in the database.
- Local development setup only: no deployment or hosting configuration; the
  `local` profile serves cookies without `Secure` over plain HTTP.
- No browser end-to-end suite and no load test yet (see
  [docs/verification.md](docs/verification.md) §5).

## Project status

Release 1 is complete: increments 1 to 9 of
[docs/implementation-plan.md](docs/implementation-plan.md) are done. It
covers sign-in with seeded demo accounts, server-side authorization,
projects (search, archive/unarchive, members), tasks (status, priority,
filters, optimistic concurrency, multiple assignees), comments and the
dashboard, verified as recorded in [docs/verification.md](docs/verification.md).
The architecture is a modular monolith; the technology stack is decided in
[ADR-0012](docs/adr/0012-technology-stack.md): Java 21 + Spring Boot, React +
Vite + TypeScript, PostgreSQL 16. Scope and requirements remain those of the
documents above; the deferred features are the next candidates.
