---
status: proposed
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0003: Single relational database

## Context and Problem Statement

Matriz data is highly relational (users–projects–tasks–comments–history) and
the reliability requirement needs ACID transactions (e.g. a status change and
its history entry). `constraints.md` listed the "persistence approach" as
pending. How should data be persisted?

## Decision Drivers

* NFR Reliability: status and history changes are persisted consistently.
* Highly relational data.
* Modular monolith with module-owned tables
  ([ADR-0001](0001-modular-monolith.md)).

## Considered Options

* Single relational database (one schema, each module owns its tables)
* Separate data stores per module/service (the "distributed data" of the
  microservices option in [ADR-0001](0001-modular-monolith.md))

## Decision Outcome

Chosen option: "Single relational database", because the data is highly
relational and needs ACID transactions. The specific engine was left open by
this decision.

> **Update:** the engine was later chosen in
> [ADR-0012](0012-technology-stack.md): **PostgreSQL 16**.

### Consequences

* Good, because status + history can be written in one transaction
  ([ADR-0008](0008-status-change-and-history-in-one-transaction.md)).
* Neutral, because module boundaries in the shared schema must be kept by
  convention: other modules access a module's tables only through the owning
  module's interface.

### Confirmation

The data model in [`database.md`](../database.md) uses a single schema;
module-boundary checks are described in [ADR-0012](0012-technology-stack.md).

## Pros and Cons of the Options

### Single relational database

* Good, because it supports ACID transactions across related entities.
* Good, because it fits highly relational data.

### Separate data stores per module/service

* Bad, because keeping status and history consistent would require
  distributed transactions or eventual consistency.

## More Information

* Data model: [`database.md`](../database.md).
