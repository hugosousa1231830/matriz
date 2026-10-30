# Architecture Decision Records

Architecture Decision Records (ADRs) for Matriz, written with the
[MADR 4.0](https://adr.github.io/madr/) template (YAML front matter with
`status`, `date`, `decision-makers`; sections *Context and Problem
Statement*, *Decision Drivers*, *Considered Options*, *Decision Outcome*
with *Consequences* and *Confirmation*, *Pros and Cons of the Options*,
*More Information*).

ADR-0001 to ADR-0011 were extracted from
[`architecture.md`](../architecture.md) (Architecture Pack) and keep
their original IDs (ADR-01 … ADR-11). Statuses were reviewed when planning release 1:
decisions confirmed for the release 1 scope are `accepted`;
decisions kept for features postponed beyond release 1 are `deferred`.

| # | Title | Status |
|---|-------|--------|
| [0001](0001-modular-monolith.md) | Matriz MVP is a modular monolith | accepted |
| [0002](0002-rest-json-api-openapi.md) | REST API with JSON, documented in OpenAPI 3.1 | accepted |
| [0003](0003-single-relational-database.md) | Single relational database | accepted |
| [0004](0004-platform-and-project-roles.md) | Administrator is a platform role; Project Manager and Member are per-project roles | accepted |
| [0005](0005-optimistic-concurrency-for-tasks.md) | Optimistic concurrency on tasks | accepted |
| [0006](0006-compute-derived-values.md) | Derived values are computed, not stored | accepted |
| [0007](0007-soft-delete-projects.md) | Soft delete — projects are archived, not removed | accepted |
| [0008](0008-status-change-and-history-in-one-transaction.md) | Status change and history entry in one transaction | deferred |
| [0009](0009-authentication-server-side-sessions.md) | Authentication with local accounts and server-side sessions | accepted |
| [0010](0010-uniform-error-format-problem-json.md) | Uniform error format `application/problem+json` | accepted |
| [0011](0011-past-deadline-confirmation-enforced-by-api.md) | Past deadline confirmation is enforced by the API | deferred |
| [0012](0012-technology-stack.md) | Technology stack — Java 21 + Spring Boot, React + Vite + TypeScript, PostgreSQL 16 | accepted |
| [0013](0013-multiple-assignees-per-task.md) | Multiple assignees per task (many-to-many) | accepted |
| [0014](0014-hard-delete-tasks.md) | Tasks are hard-deleted with their assignments and comments | accepted |
| [0015](0015-dashboard-and-project-counts.md) | Dashboard endpoint and task counts computed in SQL | accepted |
| [0016](0016-contract-first-api-and-generated-client.md) | Contract-first API, generated client and drift checks | accepted |

## Adding a new ADR

1. Copy the structure of an existing ADR and use the next number
   (`NNNN-short-title-in-kebab-case.md`).
2. Start with `status: proposed`; change to `accepted`, `rejected`,
   `deprecated`, `deferred` (postponed with its feature) or
   `superseded by ADR-NNNN` when decided.
3. Never rewrite an accepted decision — supersede it with a new ADR and link
   both.
4. Add the ADR to the table above.
