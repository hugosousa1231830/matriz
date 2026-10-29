---
status: accepted
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0001: Matriz MVP is a modular monolith

## Context and Problem Statement

Matriz needs an architectural style for the MVP (FR1–FR10 plus a simple,
in-app-only FR11). The domain is small and tightly related: tasks, comments,
history and notifications all revolve around a task, and the rule "status
change + history record" needs a single consistent transaction. Which
architectural style should the MVP use?

## Decision Drivers

* MVP goal: "simple, usable, and deliverable" (`requirements.md`).
* NFR Reliability: status and history changes are persisted consistently.
* NFR Maintainability: the solution must be easy to evolve in the next phases.
* Low operational cost while hosting/deployment is still undefined.
* Small team building from scratch (`constraints.md`).

## Considered Options

* Monolith (unstructured)
* Modular monolith
* Microservices

## Decision Outcome

Chosen option: "Modular monolith" — a single backend application exposing a
REST API, organized into domain modules with explicit interfaces, backed by a
single relational database, and consumed by a web client (browser), because:

1. It matches the MVP goal "simple, usable, and deliverable".
2. A single database with ACID transactions satisfies the reliability
   requirement (a status change and its history entry are saved atomically).
3. Module boundaries satisfy maintainability and allow later extraction (e.g.
   Notifications) if real needs appear.
4. It has low operational cost while hosting/deployment is still undefined.

### Consequences

* Good, because there is one deployable and one database, with simple ACID
  transactions.
* Good, because explicit module boundaries support future evolution and
  extraction of modules into services if ever needed.
* Bad, because there is no independent scaling per module (the whole unit is
  scaled).
* Bad, because every change requires one deployment of the whole application.

Both trade-offs were accepted given the absence of defined load targets at the
time of the decision. The load targets later quantified (5,000 users, single
instance — see [ADR-0012](0012-technology-stack.md)) remain within what one
instance can serve.

### Confirmation

* Modules communicate only through in-process interfaces (function calls or
  in-process domain events), never by reading another module's tables
  directly (`architecture.md` §4, module rules).
* With the stack chosen in [ADR-0012](0012-technology-stack.md), module
  boundaries are verified automatically by a Spring Modulith verification test
  in the build.

## Pros and Cons of the Options

Comparison for the MVP:

| Criterion | Monolith (unstructured) | **Modular monolith** | Microservices |
|-----------|-------------------------|----------------------|---------------|
| Deployment | One unit | One unit | Many independent units |
| Initial complexity | Lowest | Low | High (network, service discovery, distributed data) |
| Fit for a small team / MVP | Good | **Good** | Poor — overhead outweighs benefits |
| Internal boundaries | Weak; tends to "big ball of mud" | **Explicit modules with clear interfaces** | Strong (process/network boundaries) |
| Data consistency (status + history in one transaction) | Simple (single DB) | **Simple (single DB, ACID transactions)** | Hard (distributed transactions / eventual consistency) |
| Independent scaling | No | No (scale the whole unit) | Yes, per service |
| Evolution path | Costly refactoring later | **Modules can be extracted into services if ever needed** | Already distributed |
| Operational cost | Low | Low | High (monitoring, tracing, CI/CD per service) |

### Monolith (unstructured)

* Good, because it is the fastest to start (lowest initial complexity).
* Good, because data consistency is simple (single database).
* Good, because operational cost is low.
* Bad, because it gives no guidance on boundaries and tends to a "big ball of
  mud", which hurts the maintainability requirement as features grow.
* Bad, because evolution later requires costly refactoring.

### Modular monolith

* Good, because it keeps the simplicity of one deployable and one database.
* Good, because a single database with ACID transactions keeps status and
  history consistent.
* Good, because explicit modules with clear interfaces support maintainability
  and later extraction.
* Good, because operational cost is low.
* Bad, because modules cannot be scaled independently.

### Microservices

* Good, because boundaries are strong (process/network).
* Good, because each service can be scaled independently.
* Bad, because they solve scaling and team-autonomy problems Matriz does not
  have.
* Bad, because the "status change + history record" rule would need
  distributed transactions or eventual consistency.
* Bad, because initial complexity (network, service discovery, distributed
  data) and operational cost (monitoring, tracing, CI/CD per service) are high
  — the overhead outweighs the benefits for a small team / MVP.

## More Information

* Components and module rules: [`architecture.md`](../architecture.md) §4.
* Related: [ADR-0003](0003-single-relational-database.md) (single relational
  database), [ADR-0008](0008-status-change-and-history-in-one-transaction.md)
  (status + history in one transaction),
  [ADR-0012](0012-technology-stack.md) (technology stack).
* Revisit if real needs for independent scaling or team autonomy appear
  (e.g. extracting Notifications).
