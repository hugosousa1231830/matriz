---
status: accepted
date: 2026-10-29
decision-makers: Matriz team with the client representative (release 1 planning)
---

# ADR-0014: Tasks are hard-deleted with their assignments and comments

## Context and Problem Statement

Release 1 requires deleting tasks while applying a documented rule to
dependent records, so that no orphan data remains. Projects are archived instead
([ADR-0007](0007-soft-delete-projects.md)). How are tasks deleted?

## Decision Drivers

* Release 1: create, read, edit and **delete** tasks; no orphan records.
* `constraints.md`: destructive actions require explicit confirmation.
* Simplicity: status history is deferred, so no audit trail depends on tasks.

## Considered Options

* Hard delete, cascading assignments and comments in one transaction
* Soft delete (`deleted_at`) on tasks

## Decision Outcome

Chosen option: "Hard delete", by a Manager of the project or an
Administrator, after a confirmation dialog in the UI. `task_assignee` and
`comment` rows are deleted in the same transaction (`ON DELETE CASCADE`).
Not allowed on archived projects (`409 PROJECT_ARCHIVED`).

### Consequences

* Good, because no orphan rows and no "deleted" filter on every query.
* Bad, because a deleted task cannot be recovered; mitigated by the UI
  confirmation and by limiting the action to Managers.
* Neutral, because when status history (FR10) is implemented later this
  decision must be revisited (history rows would also cascade).

### Confirmation

Integration test: deleting a task with assignments and comments leaves no
rows referencing it; a Member receives `403`.

## Pros and Cons of the Options

### Hard delete with cascade

* Good, because it is simple and leaves no orphans.

### Soft delete

* Good, because recoverable.
* Bad, because every query must exclude deleted tasks, and release 1 requires
  deletion.
