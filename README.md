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

- Authentication and account management.
- Project management (create, edit, archive).
- Task management (create, edit, assign).
- Task statuses and flow (To do, In progress, Done).
- Priorities and deadlines.
- Task filtering and sorting.
- Project progress dashboard.
- Comments on tasks.
- Overdue task flagging.
- Change history.
- Simple in-app notifications.

Features planned for later: Viewer role, subtasks and dependencies,
attachments, email notifications, data import/export, and advanced reports.

## Documentation

| Document | Content |
|----------|---------|
| [docs/requirements.md](docs/requirements.md) | Vision, user types, functional and non-functional requirements, MVP scope |
| [docs/user-stories.md](docs/user-stories.md) | MVP user stories, priorities, acceptance criteria, exceptional cases |
| [docs/constraints.md](docs/constraints.md) | Known constraints, assumptions, security/privacy, pending decisions, scope limits |
| [docs/architecture.md](docs/architecture.md) | Architecture overview (modular monolith), technology stack summary, components, assumptions, review log |
| [docs/adr/](docs/adr/README.md) | Architecture Decision Records (MADR 4.0), incl. the technology stack decision (ADR-0012) |
| [docs/database.md](docs/database.md) | Data model, business rules, and Mermaid ER diagram |
| [openapi.yaml](openapi.yaml) | OpenAPI 3.1 contract for selected MVP operations (tasks) |
| [docs/ui/wireframe.png](docs/ui/wireframe.png) | Low-fidelity wireframe of the project dashboard (source: `docs/ui/wireframe.html`) |

## Project status

Early stage — Specification Pack (Aula 01) and simplified Architecture Pack
(Aula 02) documented. No application code yet. The architecture is a proposed
modular monolith; the technology stack is decided in
[ADR-0012](docs/adr/0012-technology-stack.md): Java 21 + Spring Boot, React +
Vite + TypeScript, PostgreSQL 16. The
next phases will confirm the pending decisions and begin implementing the MVP.

All assumptions documented here should be validated with the client before
implementation.
