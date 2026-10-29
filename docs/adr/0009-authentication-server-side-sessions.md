---
status: accepted
date: 2026-10-29
decision-makers: Matriz team (Architecture Pack, Aula 02)
---

# ADR-0009: Authentication with local accounts and server-side sessions

> **Release 1 update (accepted):** local accounts (seeded only; no public
> registration or password reset) are the authentication mechanism. Values:
> idle timeout **30 min**, absolute lifetime **8 h**; cookie `matriz_session`
> (`HttpOnly`, `SameSite=Lax`, `Secure` outside local development). CSRF:
> Spring Security cookie-to-header (`XSRF-TOKEN` cookie → `X-XSRF-TOKEN`
> header) on unsafe methods, token issued by `GET /auth/csrf`; invalid →
> `403 CSRF_INVALID`. Brute force: 5 consecutive failures lock the account
> for 15 min (reset on success) and more than 20 sign-in attempts per minute
> per IP return `429`; locked accounts get the same generic `401` as wrong
> passwords. Endpoints in [`openapi.yaml`](../../openapi.yaml) (`/auth/*`).
> Assumption A7 is closed.

## Context and Problem Statement

FR1 requires sign-in with email and password. `constraints.md` requires hashed
passwords, brute-force protection, no sensitive data in logs/errors, and that
sessions are invalid after sign-out. The authentication mechanism (local
accounts vs. external identity provider) is still pending. How should users
authenticate and how are sessions handled?

## Decision Drivers

* FR1 — sign in with email and password.
* Every security constraint in `constraints.md`.
* Sign-out must take effect immediately.

## Considered Options

* Local accounts + server-side sessions (proposal)
* External identity provider (still an option, see `constraints.md`)

## Decision Outcome

Chosen option (proposal): "Local accounts + server-side sessions":

* local accounts (email + password);
* passwords hashed with a slow adaptive algorithm (e.g. Argon2id/bcrypt);
* server-side sessions (stored in the database or a session store) referenced
  by an opaque, random ID in a `Secure`, `HttpOnly`, `SameSite=Lax` cookie;
* session ID regenerated at sign-in;
* idle and absolute expiry (values to define — assumption A7);
* session deleted on sign-out;
* state-changing requests protected against CSRF (anti-CSRF token and/or
  `Origin` check);
* failed sign-ins rate-limited with temporary lockout.

Because it directly satisfies FR1 and every listed security constraint, and
server-side sessions make sign-out effective immediately. It still requires
confirmation.

> **Update:** [ADR-0012](0012-technology-stack.md) implements this with Spring
> Security (Argon2id, CSRF, session fixation protection) and Spring Session
> JDBC, storing sessions in **PostgreSQL** (no separate session store).

### Consequences

* Good, because sign-out invalidates the session immediately.
* Neutral, because session storage adds a read per request (and periodic
  writes for last-access time).
* Neutral, because idle/absolute expiry values are still to be defined (A7).

### Confirmation

Security integration tests: session ID changes at sign-in, session is invalid
after sign-out, state-changing request without CSRF token is rejected,
repeated failed sign-ins trigger lockout.

## Pros and Cons of the Options

### Local accounts + server-side sessions

* Good, because it satisfies FR1 and every listed security constraint.
* Good, because server-side sessions make sign-out effective immediately.
* Neutral, because password storage and brute-force protection are the
  application's responsibility.

### External identity provider

* Neutral, because it remains an option pending confirmation
  (`constraints.md`).

## More Information

* Review finding R7 in [`architecture.md`](../architecture.md): session
  storage, ID rotation, expiry, `SameSite=Lax` and CSRF were added; a Session
  entity was not added to the logical model (D8).
* As built in Increment 3 (details in
  [`implementation-plan.md`](../implementation-plan.md)): Spring Security with
  Spring Session JDBC, cookie `matriz_session` (`HttpOnly`, `SameSite=Lax`,
  `Secure` outside local development); a new session is created at every
  sign-in; 30 min idle timeout and 8 h absolute limit from sign-in; CSRF
  cookie-to-header (`XSRF-TOKEN` → `X-XSRF-TOKEN`); lockout after 5 failed
  attempts for 15 min and 20 attempts per minute per client IP (`429`).
