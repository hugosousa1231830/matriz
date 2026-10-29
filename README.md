# Matriz

Matriz is a project and task management application for teams.

## Problem

Teams often lack clear, shared visibility over the status of their projects and
tasks. It is hard to know what is being worked on, by whom, what is overdue, and
how a project is progressing overall. Matriz aims to solve this by centralizing
projects and tasks in one place with a clear view of their status.

## Main objectives

- Centralize projects and tasks in a single place.
- Make the progress and status of work visible at any time.
- Support basic collaboration around tasks.
- Keep the first version simple, usable, and deliverable.

## Planned MVP features

Release 1 (in progress) implements the items marked *(release 1)*; the others
are deferred and kept as future work — see
[docs/implementation-plan.md](docs/implementation-plan.md).

- Authentication *(release 1: seeded demo accounts, sign-in/out, lockout)* and account management.
- Project management — create, edit, search, archive/unarchive, members *(release 1)*.
- Task management — create, edit, delete, multiple assignees *(release 1)*.
- Task statuses and flow (To do, In progress, Done) *(release 1)*.
- Priorities *(release 1)* and deadlines *(deferred)*.
- Task filtering and sorting *(release 1: status, priority, assignee)*.
- Project progress dashboard *(release 1)*.
- Comments on tasks *(release 1)*.
- Overdue task flagging *(deferred)*.
- Change history *(deferred)*.
- Simple in-app notifications *(deferred)*.

Features planned for later: Viewer role, subtasks and dependencies,
attachments, email notifications, data import/export, and advanced reports.

## Documentation

| Document | Content |
|----------|---------|
| [docs/requirements.md](docs/requirements.md) | Vision, user types, functional and non-functional requirements, MVP scope |
| [docs/user-stories.md](docs/user-stories.md) | MVP user stories, priorities, acceptance criteria, exceptional cases |
| [docs/constraints.md](docs/constraints.md) | Known constraints, assumptions, security/privacy, pending decisions, scope limits |
| [docs/implementation-plan.md](docs/implementation-plan.md) | Release 1 scope reconciliation, access rules, increments with acceptance criteria, verification and status |
| [docs/architecture.md](docs/architecture.md) | Architecture overview (modular monolith), technology stack summary, components, assumptions, review log |
| [docs/adr/](docs/adr/README.md) | Architecture Decision Records (MADR 4.0), incl. the technology stack decision (ADR-0012) |
| [docs/database.md](docs/database.md) | Data model (incl. task assignees N:M), business rules, and Mermaid ER diagram |
| [openapi.yaml](openapi.yaml) | OpenAPI 3.1 contract for all release 1 operations (auth, dashboard, projects, members, tasks, assignees, comments); lint with `npx --yes @redocly/cli lint openapi.yaml` |
| [docs/ui/wireframe.png](docs/ui/wireframe.png) | Low-fidelity wireframe of the project dashboard (source: `docs/ui/wireframe.html`) |

## Project status

Early stage — Specification Pack (Aula 01) and simplified Architecture Pack
(Aula 02) documented, and the release 1 implementation plan, access rules and
API contract consolidated. No application code yet. The architecture is a
modular monolith; the technology stack is decided in
[ADR-0012](docs/adr/0012-technology-stack.md): Java 21 + Spring Boot, React +
Vite + TypeScript, PostgreSQL 16. Implementation follows the increments in
[docs/implementation-plan.md](docs/implementation-plan.md).

All assumptions documented here should be validated with the client before
implementation.
