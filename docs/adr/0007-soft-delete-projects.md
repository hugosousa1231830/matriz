---
status: accepted
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0007: Soft delete — projects are archived, not removed

> **Release 1 update (accepted, confirmed with the client representative):** the release 1
> scope asks to delete projects; the reconciled decision is that projects are never
> deleted. Any Manager of the project (and Administrators) can **archive and
> unarchive** (`POST /projects/{id}/archive`, `/unarchive`) after a UI
> confirmation. Archived projects are read-only (`409 PROJECT_ARCHIVED` on
> writes), shown with an "Archived" badge when "show archived" is selected,
> and excluded from the dashboard. P10 closed: no hard delete. Tasks, by
> contrast, are hard-deleted ([ADR-0014](0014-hard-delete-tasks.md)).

## Context and Problem Statement

`requirements.md` gives Administrators permission for "deleting projects",
while `constraints.md` says to prefer soft delete (archive/recoverable) over
permanent removal where feasible. How are projects removed in the MVP?

## Decision Drivers

* Security/privacy constraint "prefer soft delete".
* FR2: create, edit, and archive projects.

## Considered Options

* Soft delete (archive, status `ARCHIVED`)
* Permanent deletion

## Decision Outcome

Chosen option: "Soft delete": projects are archived (status `ARCHIVED`), not
removed, in the MVP, because of the security/privacy constraint "prefer soft
delete".

### Consequences

* Good, because archived projects are recoverable.
* Bad, because it narrows the Administrator permission "deleting projects" —
  pending confirmation ([`database.md`](../database.md) P10).

### Confirmation

`Project.status` enum `ACTIVE`/`ARCHIVED` in [`database.md`](../database.md)
(D7); no delete operation for projects in the API.

## Pros and Cons of the Options

### Soft delete

* Good, because it follows the "prefer soft delete" constraint.

### Permanent deletion

* Good, because it matches the literal Administrator permission "deleting
  projects".
* Bad, because it is not recoverable, against the "prefer soft delete"
  constraint.

## More Information

* Related: FR2, `constraints.md`, review finding R14 in
  [`architecture.md`](../architecture.md).
