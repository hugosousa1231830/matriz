---
status: proposed
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0006: Derived values are computed, not stored

## Context and Problem Statement

FR7 (project progress) and FR9 (overdue flagging), plus exceptional case 4
(project with no tasks), depend on values derived from tasks: "overdue" and
project progress (% Done). Should these values be stored or computed?

## Decision Drivers

* Values must always be consistent with the tasks.
* Avoid extra background jobs.

## Considered Options

* Compute on read
* Store flags/values and keep them updated (e.g. by a job)

## Decision Outcome

Chosen option: "Compute on read": "overdue" (deadline before today and status
≠ Done) and project progress (% Done) are derived, because this avoids stale
flags and extra jobs and is always consistent.

> **Note:** "today" is evaluated in the user's time zone — see revised
> assumption A4 in [`architecture.md`](../architecture.md) §6 and
> [ADR-0012](0012-technology-stack.md).

### Consequences

* Good, because values are never stale.
* Good, because no job is needed to refresh flags.
* Neutral, because aggregation runs on each read; indexes such as
  `(project_id, status)` on tasks keep this within the performance targets
  (see [ADR-0012](0012-technology-stack.md)).

### Confirmation

No `is_overdue` or progress column exists in [`database.md`](../database.md);
the API marks `isOverdue` as derived ([`openapi.yaml`](../../openapi.yaml)).

## Pros and Cons of the Options

### Compute on read

* Good, because it is always consistent.
* Good, because it avoids extra jobs.

### Store and update

* Bad, because stored flags can become stale.
* Bad, because it needs extra jobs to keep them updated.

## More Information

* Related: FR7, FR9, exceptional case 4.
