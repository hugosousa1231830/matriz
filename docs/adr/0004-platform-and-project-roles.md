---
status: proposed
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0004: Administrator is a platform role; Project Manager and Member are per-project roles

## Context and Problem Statement

`requirements.md` states that the Administrator "manages the platform and
users", while `constraints.md` assumes a user can belong to multiple projects
"with one role per project". Where should each role live?

## Decision Drivers

* Reconcile "Administrator manages the platform" with "one role per project".
* Server-side authorization for every action (`constraints.md` security).

## Considered Options

* Administrator as a platform-level role on the user; Project Manager and
  Member as per-project roles stored in the membership

No other options were recorded in the Architecture Pack.

## Decision Outcome

Chosen option: "Administrator is a *platform-level* role on the user; Project
Manager and Member are *per-project* roles stored in the membership", because
it reconciles "Administrator manages the platform" with the assumption "one
role per project".

### Consequences

* Good, because a user can be Project Manager in one project and Member in
  another.
* Neutral, because Administrator visibility over all projects is recorded as
  decision D13 in `database.md`.

### Confirmation

`User.is_admin` and `ProjectMember.role` in [`database.md`](../database.md);
authorization checks reviewed in code review.

## Pros and Cons of the Options

### Platform role + per-project roles

* Good, because it reconciles the user-type table in `requirements.md` with
  the "one role per project" assumption.

## More Information

* Related: `requirements.md` user types; `constraints.md` assumptions.
