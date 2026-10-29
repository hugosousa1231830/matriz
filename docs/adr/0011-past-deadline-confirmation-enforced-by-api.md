---
status: deferred
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0011: Past deadline confirmation is enforced by the API

> **Status: deferred (release 1).** Deadlines (FR5 deadline part, FR9) are out
> of the release 1 scope; this decision is kept for when deadlines return.

## Context and Problem Statement

Exceptional case 1 (`user-stories.md`): setting a deadline in the past must
require explicit confirmation. Where is this rule enforced?

## Decision Drivers

* Exceptional case 1 must hold for any client.

## Considered Options

* Enforce in the API (`confirmPastDeadline: true`)
* Enforce only in the web UI

## Decision Outcome

Chosen option: "Enforce in the API": a deadline earlier than today is rejected
unless the request includes `confirmPastDeadline: true`, because it makes
exceptional case 1 hold for any client, not only the web UI.

> **Note:** "today" is evaluated in the user's time zone — see revised
> assumption A4 in [`architecture.md`](../architecture.md) §6 and
> [ADR-0012](0012-technology-stack.md).

### Consequences

* Good, because the rule cannot be bypassed by another client.
* Neutral, because the UI still shows a warning and sends the flag after the
  user confirms.

### Confirmation

The API returns `422` for a past deadline without `confirmPastDeadline`
([`openapi.yaml`](../../openapi.yaml)); covered by an API test.

## Pros and Cons of the Options

### Enforce in the API

* Good, because the rule holds for any client.

### Enforce only in the web UI

* Bad, because other clients could bypass the confirmation.

## More Information

* Related: exceptional case 1, BR9 in [`database.md`](../database.md).
