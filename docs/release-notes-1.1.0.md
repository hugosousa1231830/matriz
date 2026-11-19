# Matriz 1.1.0 — Release notes

Version **1.1.0** (2026-11-19) adds backward-compatible features to
[1.0.0](release-notes-1.0.0.md): a restyled user interface, a Filters
toolbar, member candidates without a search term and a Render deployment.
The full list of changes is in [CHANGELOG.md](../CHANGELOG.md).

## Versioning

- The application follows [Semantic Versioning](https://semver.org/):
  backend (`backend/pom.xml`) and frontend (`frontend/package.json`) are both
  `1.1.0`, a minor version because every change is backward compatible.
- The API contract `openapi.yaml` moves from 0.2.3 to **0.3.0** (see
  below). The base path stays `/api/v1`.
- Tagging: the tag `v1.1.0` on the release commit is a step for the release
  manager. The version links at the end of `CHANGELOG.md` resolve once the
  tag exists.

## Highlights

- Restyled user interface without new dependencies: design tokens
  (colours, radii, shadows, motion), frosted surfaces, initials avatars and
  friendlier empty states and skeleton loaders.
- The project's tasks are a card-based board with To do, In progress and
  Done columns, each with its own count and pagination; task and project
  cards are pressable. A column stays on a valid page after a task moves to
  another status.
- Reduced-motion support: animations, transitions and the press delay are
  turned off when the system asks for reduced motion
  (`prefers-reduced-motion: reduce`).
- A **Filters** toolbar on the project page with **Status** and
  **Priority** dropdowns (several values can still be ticked) next to
  **Assignee** and **Sort by**.
- In **Add a member**, clicking **Search** with an empty field lists the
  active users who are not members yet.
- Render deployment: `render.yaml` Blueprint with a free Render Postgres 16
  database, the API and the nginx SPA; see the README "Deploy to Render".

Step-by-step usage is in the [user guide](user-guide.md) (§10 members,
§11 task board and filters).

## API contract change (0.3.0)

- `GET /projects/{projectId}/member-candidates`: the `search` query
  parameter is now optional. Without it (absent or blank) the response lists
  all active users who are not members yet, sorted by name, at most 20. A
  present search still needs 2 to 100 characters.
- Backward compatible (minor version): every request valid under 0.2.3 is
  still valid and answers the same way; no operation, field or error was
  removed or renamed. The generated client `frontend/src/api/schema.d.ts`
  matches the contract.

## Upgrade notes

- Database: no new Flyway migration since 1.0.0 (still `V1__schema.sql` and
  `V2__spring_session.sql`); an existing database is only validated.
- Configuration: no variable was added or renamed in `.env.example`. The SPA
  image now renders its nginx configuration from
  `frontend/nginx.conf.template`; `MATRIZ_API_UPSTREAM` (default
  `http://backend:8080`) and `MATRIZ_API_HOST_HEADER` are optional, so
  Docker Compose behaves as before.
- Data: the named volume `matriz-db-data` is kept; upgrade with
  `docker compose up -d --build --wait`.

## Verification

Local checks with version 1.1.0 (2026-11-19):

| Check | Result |
|-------|--------|
| `cd backend && ./mvnw -q verify` | BUILD SUCCESS, 175 tests, 0 failures, 0 errors; jar `matriz-backend-1.1.0.jar` |
| `cd frontend && npm test` / `npm run lint` / `npm run build` / `npm run api:check` | 5 files, 66 tests passed / exit 0 / exit 0 / exit 0 |
| `npx --yes @redocly/cli@2.79.0 lint openapi.yaml` | Valid (1 problem explicitly ignored in `redocly.yaml`) |

CI on GitHub (workflow `CI`, `push`) passed on the last commit before the
version change:
[44536677619](https://github.com/hugosousa1231830/matriz/actions/runs/44536677619)
(`f89d006`, all jobs including the Compose build and E2E).

Known limitations are those of [1.0.0](release-notes-1.0.0.md#known-limitations),
except that a deployment now exists (Render); behind Render's proxies the
per-IP sign-in rate limit has no effect (README "Deploy to Render").
