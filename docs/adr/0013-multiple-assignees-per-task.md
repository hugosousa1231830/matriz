---
status: accepted
date: 2026-10-29
decision-makers: Matriz team with the client representative (release 1 planning)
---

# ADR-0013: Multiple assignees per task (many-to-many)

## Context and Problem Statement

The Architecture Pack model gave each task a single owner (`Task.assignee_id`), as
assumed in `constraints.md`. Release 1 requires assigning several eligible users to a task and
removing each assignment, and forbids duplicate associations. How are assignees modelled?

## Decision Drivers

* Release 1 functional scope: several assignees per task, each removable.
* No duplicate (task, user) association, enforced by the backend.
* Assignees must be eligible users (members of the task's project).

## Considered Options

* Join table `task_assignee (task_id, user_id)` with a composite primary key
* Array of user IDs on the task row
* Keep a single `assignee_id`

## Decision Outcome

Chosen option: "Join table `task_assignee`" with PK (`task_id`, `user_id`),
`assigned_at` and `assigned_by_id`, because it is the relational way to model
N:M, gives uniqueness for free and supports the `assigneeId` filter with an
index.

API: `POST /tasks/{taskId}/assignees` (`409 ALREADY_ASSIGNED` on duplicates,
`422 ASSIGNEE_NOT_ELIGIBLE` for unknown/non-member users) and
`DELETE /tasks/{taskId}/assignees/{userId}`; tasks may be created with
`assigneeIds` (0..N). Only Managers of the project and Administrators change
assignees. Assignment changes do not increment the task `version`.

### Consequences

* Good, because duplicates are impossible at database level.
* Good, because removing a member deletes only their assignment rows (BR7).
* Neutral, because "task owner" in the requirements now means "any assignee"
  for edit permissions (BR3), and the single-owner assumption in
  `constraints.md` is superseded.
* Bad, because task reads need an extra query/join to load assignees (one
  batched query per page, no N+1).

### Confirmation

Composite PK in the Flyway migration; integration tests: duplicate assignment
returns `409` and leaves one row; non-member returns `422`.

## Pros and Cons of the Options

### Join table

* Good, because uniqueness and foreign keys are enforced by PostgreSQL.

### Array column

* Bad, because no foreign keys or uniqueness per element.

### Single `assignee_id`

* Bad, because it does not meet the release 1 scope.

## More Information

* Supersedes the single-owner design D1 in [`database.md`](../database.md).
