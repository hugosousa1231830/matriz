---
status: accepted
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0010: Uniform error format `application/problem+json`

## Context and Problem Statement

The client needs consistent error handling, and `constraints.md` requires that
sensitive data is not exposed in error messages. What error format should the
API use?

## Decision Drivers

* Consistent client handling.
* No sensitive data in errors (`constraints.md` security).

## Considered Options

* `application/problem+json` (RFC 9457) with generic messages

No other options were recorded in the Architecture Pack.

## Decision Outcome

Chosen option: "`application/problem+json` (RFC 9457) with generic messages",
because it gives consistent client handling and no sensitive data in errors.

### Consequences

* Good, because every error has the same shape.
* Neutral, because the backend needs one central exception-to-problem mapping
  (provided by Spring's `ProblemDetail` support — see
  [ADR-0012](0012-technology-stack.md)).

### Confirmation

Error responses in [`openapi.yaml`](../../openapi.yaml) use
`application/problem+json`; tests assert no stack traces or internal details
appear in error bodies.

## Pros and Cons of the Options

### `application/problem+json` (RFC 9457)

* Good, because client handling is consistent.
* Good, because generic messages avoid leaking sensitive data.

## More Information

* Related: `constraints.md` security.
