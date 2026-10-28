# Matriz — Constraints and Assumptions

## Known constraints

- The project currently has no application code; only IDE configuration files
  exist. The MVP will be built from scratch.
- The initial request did not specify technologies, platform, or interface
  preferences.
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
- Each task has a single owner; followers are a future feature.
- Project progress is computed from the proportion of tasks in the Done status.
- Notifications in the MVP are limited to in-app alerts.
- Specific non-functional targets (concurrent users, response times, data
  volumes) are not yet defined.

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

- Technology stack (frontend, backend, database) — intentionally **open**.
- Deployment and hosting environment.
- Authentication mechanism (e.g., local accounts vs. external identity provider).
- Password reset / account recovery flow (deferred — see requirements scope).
- Data model and persistence approach.
- Testing strategy and tooling.
- How concurrent edits (conflict detection) will be implemented.

## Scope limitations

The following are explicitly out of the initial scope (MVP):

- Viewer (read-only) role.
- Subtasks and dependencies between tasks.
- Task attachments.
- Email notifications.
- Data import/export.
- Reports and advanced metrics.
