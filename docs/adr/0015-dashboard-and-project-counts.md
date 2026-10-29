---
status: accepted
date: 2026-10-29
decision-makers: Matriz team with the client representative (release 1 planning)
---

# ADR-0015: Dashboard endpoint and task counts computed in SQL

## Context and Problem Statement

The initial dashboard shows the accessible projects with task counts per
status and navigation to the detail; release 1 requires totals that reflect
the relevant data set, not only the visible page. The Architecture Pack only planned a
per-project summary endpoint. Where do the counts come from?

## Decision Drivers

* Counts must reflect all tasks, not a page of the task list.
* Few requests per screen; p95 < 200 ms for lists/dashboard.
* Counts refresh after every task mutation (TanStack Query invalidation).

## Considered Options

* `GET /dashboard` + counts embedded in every `Project` representation
* Separate `GET /projects/{id}/summary` per project
* Compute counts in the browser from the task list

## Decision Outcome

Chosen option: "`GET /dashboard` + embedded counts". `GET /dashboard`
returns the caller's accessible **active** projects (paginated, sorted by
name) with `taskCounts` (`todo`, `inProgress`, `done`, `total`) and
`progressPercent`, plus `totals` over all those projects. Every `Project`
returned by `GET /projects` and `GET /projects/{id}` embeds the same counts,
so the project detail cards need no extra call. Counts are computed with one
`GROUP BY project_id, status` query per page (index on
`task (project_id, status)`). Archived projects are excluded from the
dashboard.

### Consequences

* Good, because one request renders the dashboard, and counts are always
  consistent with the tasks ([ADR-0006](0006-compute-derived-values.md)).
* Neutral, because the frontend must invalidate the dashboard, project and
  task-list queries after task create/edit/delete.
* Neutral, because overdue/unassigned counts from the Architecture Pack wireframe are
  deferred with deadlines.

### Confirmation

Integration test: counts match the seeded tasks and exclude archived
projects; a project with no tasks returns `progressPercent = 0`.

## Pros and Cons of the Options

### `GET /dashboard` + embedded counts

* Good, because a single round trip and no client-side aggregation.

### Per-project summary endpoint

* Bad, because the dashboard would need N+1 requests.

### Client-side counts

* Bad, because the task list is paginated, so counts would be wrong.
