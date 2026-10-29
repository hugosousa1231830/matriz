---
status: proposed
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0002: REST API with JSON, documented in OpenAPI 3.1

## Context and Problem Statement

The web client (SPA) and the modular-monolith backend
([ADR-0001](0001-modular-monolith.md)) need a contract to communicate for all
functional requirements. Which API style and contract format should be used?

## Decision Drivers

* Simplicity and wide support.
* Contract-first work for frontend and backend.
* Coverage of all FRs.

## Considered Options

* REST API with JSON over HTTPS, documented in OpenAPI 3.1, path-versioned
  (`/api/v1`)

No other options were recorded in the Architecture Pack.

## Decision Outcome

Chosen option: "REST API with JSON over HTTPS, documented in OpenAPI 3.1
(`openapi.yaml`), path-versioned (`/api/v1`)", because it is simple, widely
supported, and contract-first for frontend/backend work.

### Consequences

* Good, because frontend and backend can work in parallel against one
  contract.
* Good, because the OpenAPI document can be used to generate a typed API
  client (see [ADR-0012](0012-technology-stack.md)).
* Neutral, because operations not yet specified in `openapi.yaml` (e.g. the
  planned project summary endpoint) must be added before implementation.

### Confirmation

Implemented endpoints match [`openapi.yaml`](../../openapi.yaml); reviewed in
design/code review.

## Pros and Cons of the Options

### REST API with JSON, OpenAPI 3.1, `/api/v1`

* Good, because it is simple and widely supported.
* Good, because it enables contract-first frontend/backend work.

## More Information

* Contract: [`openapi.yaml`](../../openapi.yaml).
* Planned, not yet specified: project summary endpoint
  (`architecture.md` §7).
