---
status: deferred
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack)
---

# ADR-0008: Status change and history entry in one transaction

> **Status: deferred (release 1).** Status history (FR10) is out of the release 1
> scope and will be implemented later; the decision is kept unchanged for
> that phase. In-app notifications (FR11) are deferred as well.

## Context and Problem Statement

FR10 / US4 require recording every task status change (previous status, new
status, author, date). How do we guarantee history never diverges from the
task?

## Decision Drivers

* NFR Reliability: status and history changes are persisted consistently.

## Considered Options

* Write the status change and the history entry in one database transaction
* Write them separately (e.g. asynchronously / eventually consistent)

## Decision Outcome

Chosen option: "One transaction", because history can never diverge from the
task. The same in-process, synchronous approach is used for the
`TASK_ASSIGNED` notification (`architecture.md` §4).

### Consequences

* Good, because history is always consistent with the task.
* Neutral, because modules (Tasks → Task History, Notifications) collaborate
  through synchronous in-process interfaces/events inside the transaction.

### Confirmation

Integration test: a status change that fails to write history rolls back the
task change (see testing approach in [ADR-0012](0012-technology-stack.md)).

## Pros and Cons of the Options

### One transaction

* Good, because history cannot diverge from the task.
* Good, because it relies on the single relational database
  ([ADR-0003](0003-single-relational-database.md)).

### Separate writes

* Bad, because a failure between writes can leave history inconsistent.

## More Information

* Review finding R6 in [`architecture.md`](../architecture.md): a
  transactional outbox for notifications was rejected as over-engineering for
  the MVP.
