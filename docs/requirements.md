# Matriz — Requirements

> The user roles, task statuses, and permissions below derive from assumptions
> made during requirements analysis. They are documented as requirements for
> planning, but remain **unvalidated** until confirmed with the client — see
> `constraints.md`.

## Vision and objectives

Matriz is a project and task management application. Its goal is to give teams
clear visibility over the status of their projects and tasks, so that everyone
knows, at any time, what is being worked on, by whom, and what is still missing.

Main objectives:
- Centralize projects and tasks in a single place.
- Make the progress and status of work visible.
- Support basic collaboration around tasks.
- Keep the first version simple, usable, and deliverable.

## User types

| Role | Description | Main permissions |
|------|-------------|------------------|
| Administrator | Manages the platform and users | Everything, including deleting projects and managing accounts. *Release 1:* sees and manages all projects (archive instead of delete); accounts come only from the seed, no admin UI |
| Project Manager | Responsible for one or more projects | Create/edit projects, manage members and tasks. *Release 1:* any signed-in user can create a project and becomes its owner and Project Manager |
| Member | Carries out assigned tasks | View projects they participate in, manage tasks assigned to them, comment |
| Viewer *(future)* | Read-only access | View projects and tasks without editing |

## Functional requirements

- **FR1 — Authentication and accounts:** users sign in with email and password;
  actions are tied to the authenticated user.
- **FR2 — Project management:** create, edit, and archive projects with name,
  description, owner, members, status, start date, and expected end date.
  *Release 1:* name, description, owner and status (active/archived); projects
  can be archived and unarchived (never deleted); Project Managers add/remove
  members and change their role; start and expected end dates are
  **deferred**.
- **FR3 — Task management:** Project Managers create and assign tasks; Members
  manage (edit/update) tasks assigned to them. A task has a title, description,
  owner, status, priority, deadline, and an associated project.
  *Release 1:* a task has 0..N assignees (members of its project, no
  duplicates — [ADR-0013](adr/0013-multiple-assignees-per-task.md)); Project
  Managers can delete tasks after a confirmation
  ([ADR-0014](adr/0014-hard-delete-tasks.md)); the deadline is **deferred**.
- **FR4 — Task statuses and flow:** tasks move through To do, In progress, and
  Done. The task owner (assigned Member) and the Project Manager can change a
  task's status. *Release 1:* any assignee of the task can change it.
- **FR5 — Priorities and deadlines:** tasks have a priority (Low, Medium, High)
  and a deadline, editable after creation by the task owner or the Project
  Manager. *Release 1:* priority only; the deadline part is **deferred**.
- **FR6 — Filtering and sorting:** tasks can be filtered and sorted by status,
  priority, owner, and deadline. *Release 1:* filter by status, priority and
  assignee; sort by priority, status, title or creation date; deadline
  filters/sort **deferred**. Projects can be searched by name.
- **FR7 — Project progress dashboard:** show overall project progress based on
  the proportion of tasks with the Done status. *Release 1:* a dashboard lists
  the user's accessible active projects with task counts per status and
  % Done ([ADR-0015](adr/0015-dashboard-and-project-counts.md)).
- **FR8 — Comments:** users can comment on tasks.
- **FR9 — Overdue flagging** *(deferred beyond release 1)***:** tasks past their deadline and not completed are
  marked as overdue.
- **FR10 — Change history** *(deferred beyond release 1)***:** record task status changes (previous status, new
  status, author, date).
- **FR11 — In-app notifications** *(deferred beyond release 1)***:** alert users about assignments and deadlines
  (simple version in the MVP).

## Non-functional requirements

- **Usability:** clear interface that lets a user understand a project's status
  with minimal effort (to be validated with users).
- **Performance:** common views must be quick. Server-side p95 response time
  (excluding network) < 200 ms for lists and the dashboard, and < 500 ms for
  writes, at the expected load: 5,000 users who may all be concurrent (same
  organization, same working hours), estimated peak ~300–500 requests/s
  including notification polling every 30–60 s (~85–170 requests/s).
- **Data volume:** after year 1, ~5k projects, ~500k tasks and ~2M comments.
- **Scalability:** growth of ~2× in 2 years (≈ 10,000 users) without
  architectural change.
- **Availability:** ~99% during business hours; a single instance in a single
  region; brief downtime for deploys is acceptable. No real-time updates are
  required — refresh/polling is acceptable.
- **Internationalization of dates:** users work across regions; dates such as
  "today", overdue and "deadline approaching" are evaluated in each user's
  time zone.
- **Security:** access requires authentication; actions respect each role's
  permissions, enforced server-side (not only hidden in the interface).
- **Privacy:** users only see projects and tasks they are entitled to.
- **Reliability:** status and history changes are persisted consistently.
- **Maintainability:** the solution should be organized to ease evolution in the
  next phases.

> Note: the quantified targets above were provided by the client
> representative and drive the technology stack decision
> ([ADR-0012](adr/0012-technology-stack.md)); they are verified with a load
> test (k6).

## MVP scope

The MVP includes the essential features that let a team manage projects and
tasks with visibility over progress:

- FR1 to FR10.
- A simple version of FR11 (in-app alerts only).

### Release 1 implementation scope

Release 1 is the first full-stack implementation: a subset of the MVP plus
the features below. Details, access rules and increments
are in [`implementation-plan.md`](implementation-plan.md).

- **In:** FR1 (seeded accounts, sign-in/out, current user, lockout and rate
  limiting); FR2 (create, list, search, read, edit, archive/unarchive,
  members); FR3–FR4; FR5 (priority); FR6 (status, priority, assignee); FR7
  (dashboard); FR8; optimistic concurrency on tasks.
- **Deferred (kept as future work, not removed):** FR5 deadline, FR9, FR10,
  FR11 and the scheduler job, project start/end dates, per-user time zone,
  k6 load test.
- **Recorded difference:** the release 1 scope asks to *delete* projects; following
  the approved soft-delete decision ([ADR-0007](adr/0007-soft-delete-projects.md)),
  projects are archived (read-only, kept with their tasks and comments) and
  can be unarchived.
- **Validation limits:** project name 3–100, description ≤ 1000; task title
  3–150, description ≤ 2000; comment 1–2000; text trimmed, whitespace-only
  rejected.

## Features out of the initial scope

- Viewer role (read-only access).
- Task review step / "In review" status.
- Subtasks and dependencies between tasks.
- Task attachments.
- Email notifications.
- Password reset / account recovery (planned, not yet scoped).
- Public self-registration and an account administration UI (release 1 uses
  seeded accounts only).
- Data import/export.
- Reports and advanced metrics.
