# Matriz — Constraints and Assumptions

## Known constraints

- The project currently has no application code; The MVP will be built from scratch.
- The initial request did not specify technologies, platform, or interface
  preferences; the technology stack was later chosen in
  [ADR-0012](adr/0012-technology-stack.md).
- The MVP must remain simple and deliverable, prioritizing clarity and tracking
  over advanced features.

## Assumptions made

These assumptions come from the requirements analysis and must be
validated with the client before implementation:

- Matriz is used by **teams** within an organization, not by a single user.
- There are three roles for the MVP: Administrator, Project Manager, and Member
  (Viewer is a future role).
- A user can belong to multiple projects, with one role per project.
- Task statuses are: To do, In progress, Done.
- Each task has a single owner; 
- Project progress is computed from the proportion of tasks in the Done status.
- Notifications in the MVP are limited to in-app alerts.
- Non-functional targets are now quantified (see `requirements.md`): 5,000
  users (all may be concurrent, peak ~300–500 requests/s), p95 < 200 ms for
  lists/dashboard and < 500 ms for writes, ~5k projects / ~500k tasks / ~2M
  comments after year 1, ~2× growth in 2 years, ~99% availability in business
  hours on a single instance in a single region, no real-time updates, and
  dates evaluated in each user's time zone.

## Security and privacy requirements

- Access to the application requires authentication.
- Actions must respect each role's permissions (authorization), enforced on the
  server side and not only hidden in the interface.
- Users only see projects and tasks they are entitled to.
- Passwords must be stored securely (hashed, never in plain text).
- Sign-in must be protected against brute-force attempts (e.g., rate limiting
  and/or temporary account lockout).
- Destructive actions (e.g., deleting a project) must require explicit
  confirmation; prefer soft delete (archive/recoverable) over permanent removal
  where feasible.
- Sensitive data must not be exposed in logs or error messages.
- Session handling must prevent unauthorized access after sign-out.

## Pending technical decisions

- Deployment and hosting environment.
- Authentication mechanism (e.g., local accounts vs. external identity provider).
- Password reset / account recovery flow (deferred — see requirements scope).

Resolved: technology stack and testing strategy —
[ADR-0012](adr/0012-technology-stack.md) (accepted); data model and
persistence approach — [ADR-0003](adr/0003-single-relational-database.md) and
`database.md` (proposed); concurrent edits —
[ADR-0005](adr/0005-optimistic-concurrency-for-tasks.md) (proposed). See
[`docs/adr/`](adr/README.md).

## Scope limitations

The following are explicitly out of the initial scope (MVP):

- Viewer (read-only) role.
- Subtasks and dependencies between tasks.
- Task attachments.
- Email notifications.
- Data import/export.
- Reports and advanced metrics.
