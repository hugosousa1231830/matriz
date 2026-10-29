---
status: proposed
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0005: Optimistic concurrency on tasks

## Context and Problem Statement

Exceptional case 3 (`user-stories.md`): concurrent edits must not silently
overwrite changes. How should conflicting task updates be detected?

## Decision Drivers

* Concurrent edits must not silently overwrite changes.
* Simplicity (no locks held across user interactions).

## Considered Options

* Optimistic concurrency with a `version` number
* Pessimistic locking (locks)
* Last write wins (no detection)

## Decision Outcome

Chosen option: "Optimistic concurrency" using a `version` number on tasks; a
stale update returns `409 Conflict` and the client reloads, because it
implements exceptional case 3 without locks.

### Consequences

* Good, because no locks are held while users edit.
* Neutral, because clients must send the `version` they read and handle `409`
  by reloading.

### Confirmation

`Task.version` starts at 1 and is incremented on every update
([`database.md`](../database.md)); the API returns `409` on a stale version
([`openapi.yaml`](../../openapi.yaml)). With the stack in
[ADR-0012](0012-technology-stack.md) this is implemented with JPA `@Version`
and covered by an integration test.

## Pros and Cons of the Options

### Optimistic concurrency with `version`

* Good, because it implements exceptional case 3 without locks.

### Pessimistic locking

* Bad, because it requires locks, which this decision explicitly avoids.

### Last write wins

* Bad, because it silently overwrites changes, violating exceptional case 3.

## More Information

* Related: exceptional case 3 (`user-stories.md`).
