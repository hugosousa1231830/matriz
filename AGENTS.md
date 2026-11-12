# Agent Instructions

Persistent instructions for coding agents working on the Matriz project.

## Before starting

- Read the relevant documentation first: `README.md`, `docs/requirements.md`,
  `docs/user-stories.md`, and `docs/constraints.md`.
- Architecture decisions are recorded as ADRs in `docs/adr/` (MADR 4.0);
  record new decisions there and link them from `docs/architecture.md`.
- Base your work on what is documented there; treat those files as the source
  of truth for scope, requirements, and decisions.

- Release 1 work is organized in increments in `docs/implementation-plan.md`
  (access rules in §4, status table in §1, change log in §8); verification
  results are recorded in `docs/verification.md`.

## Run and verify

Setup and details are in `README.md`. Run each command from the repository
root (Docker must be running; `cp .env.example .env` first):

```sh
docker compose up -d db                  # PostgreSQL 16 (volume matriz-db-data)
cd backend && ./mvnw spring-boot:run     # API (local profile reads ../.env; migrations + demo seed)
cd frontend && npm ci && npm run dev     # SPA on http://localhost:5173, proxies /api
```

Or run the whole stack in containers: `docker compose up -d --build`
(logs: `docker compose logs -f backend`); see README "Run with Docker Compose".

Before every commit run the checks that cover the change, and all of them
before closing an increment:

```sh
cd backend && ./mvnw verify          # all backend tests (Testcontainers PostgreSQL)
cd frontend && npm test              # Vitest + React Testing Library
cd frontend && npm run lint          # oxlint
cd frontend && npm run build         # type check + production build
cd frontend && npm run api:check     # generated API types match openapi.yaml
npx --yes @redocly/cli lint openapi.yaml
scripts/semgrep.sh                   # Semgrep in Docker; triage the findings
```

If Maven cannot reach its repository through a mirror configured in
`~/.m2/settings.xml`, run the wrapper with a settings file that uses Maven
Central (`./mvnw -s <file> …`); never commit such a file. The database can be
inspected with `docker compose exec db psql -U matriz -d matriz`.

## Code conventions

- **Contract first:** change `openapi.yaml` before the code, then run
  `npm run api:generate` in `frontend/` and commit `src/api/schema.d.ts`.
  Backend MockMvc API tests extend `ApiIntegrationTest` and assert
  `matchesContract()`, so responses must match the contract.
- **Modules:** backend modules are the packages under `com.matriz`
  (`identity`, `projects`, `tasks`, `comments`, `dashboard`, `demo`,
  `shared`), checked by Spring Modulith in `ModularityTests`. Use another
  module only through its public types; keep the rest package-private.
- **Authorization:** every project-scoped operation starts with
  `ProjectAccessPolicy` using the identity from the session; never trust
  identifiers or ownership sent by the client. Hidden projects answer `404`.
- **Errors:** throw `ApiException` with a `ProblemCode`; responses are
  `application/problem+json` (ADR-0010). Never expose internal details.
- **Database:** schema changes are new Flyway migrations
  (`V3__…sql`, …); never edit an applied migration.
- **Frontend:** call the API only through `src/api/*` (typed
  `openapi-fetch` client, CSRF header added there); UI text in English; use
  the shared components in `src/components/` for forms, feedback and
  confirmation dialogs.
- **Tests:** add or update tests with every behaviour change; frontend tests
  use the in-memory fake API in `src/test/fakeApi.ts`.
- **Docs:** update the increment's status, "As built" notes and change log
  in `docs/implementation-plan.md`, and any affected document (README,
  ADRs, `database.md`, `verification.md`). Repository files describe the
  product only (release 1, increments).

## Requirements and constraints

- Respect the documented functional and non-functional requirements and the
  defined MVP scope.
- Do not implement features listed as out of scope unless explicitly asked.
- Honor the documented constraints, assumptions, and security/privacy
  requirements.

## No invented features or decisions

- Do not invent features, requirements, or decisions that are not approved or
  documented.
- If something is undefined (e.g., a pending technical decision), ask or clearly
  propose an option instead of assuming one silently.

## Plan before significant changes

- Present a short plan before making significant or wide-reaching changes, and
  proceed once it is clear.

## Stay within scope

- Only modify files related to the current task.
- Avoid changes to unrelated files, configuration, or structure.
- Keep all project-relevant information in the canonical documents above.

## Approval for destructive or sensitive actions

- Request approval before destructive or sensitive actions (e.g., deleting
  files or data, overwriting large portions of content, changing security
  behavior).

## Security and confidentiality

- Never expose, hardcode, or commit credentials, secrets, or confidential
  information.
- Do not include sensitive data in logs, error messages, or documentation.

## Communication

- Communicate the assumptions you made, the changes you performed, and the
  verifications or checks you ran.
