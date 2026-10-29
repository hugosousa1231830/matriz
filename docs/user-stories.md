# Matriz — User Stories

Initial backlog for the MVP, derived from the requirements. Stories are
prioritized and include acceptance criteria written in a verifiable way
(Given / When / Then where appropriate).

> **Release 1 scope:** US9–US11, the deadline part of US2/US5/US6 and the
> history criterion of US4 are **deferred** (kept below as future work).
> Release 1 adds multiple assignees per task, task delete, project
> archive/unarchive and minimal member management — see
> [`implementation-plan.md`](implementation-plan.md).

## User Stories (MVP)

| ID | User Story | Feature | Priority |
|----|------------|---------|----------|
| US1 | As a **user**, I want to **sign in with email and password**, so that **I can securely access my projects**. | FR1 | High |
| US2 | As a **Project Manager**, I want to **create a project with a name, description, and dates** *(dates deferred)*, so that **I can organize the team's work**. | FR2 | High |
| US3 | As a **Project Manager**, I want to **create tasks and assign them to members**, so that **I can distribute the work**. | FR3 | High |
| US4 | As a **Member**, I want to **change the status of my tasks**, so that **I can reflect the progress of the work**. | FR4 | High |
| US5 | As a **Member**, I want to **set priority and a deadline** *(deadline deferred)* **on tasks**, so that **I know what is most urgent**. | FR5 | High |
| US6 | As a **Member**, I want to **filter and sort tasks by status, priority, and deadline** *(deadline deferred)*, so that **I can quickly find what is relevant**. | FR6 | Medium |
| US7 | As a **Project Manager**, I want to **see the overall progress of the project**, so that **I know the status without reviewing each task**. | FR7 | Medium |
| US8 | As a **Member**, I want to **comment on a task**, so that **I can share context with the team**. | FR8 | Medium |
| US9 | As a **Project Manager**, I want **overdue tasks to be clearly flagged**, so that **I can spot deadline risks**. | FR9 | Medium — *deferred* |
| US10 | As a **Project Manager**, I want to **see the history of a task's status changes**, so that **I can understand how it progressed**. | FR10 | Medium — *deferred* |
| US11 | As a **Member**, I want to **receive in-app notifications when a task is assigned to me or is near its deadline**, so that **I don't miss important work**. | FR11 | Low — *deferred* |

## Acceptance criteria

### US1 — Authentication (High)
- **Given** a registered user, **when** they enter the correct email and
  password, **then** they are authenticated and taken to the project list.
- **Given** a user, **when** they enter invalid credentials, **then** an error
  message is shown and access is denied.
- **Given** an unauthenticated user, **when** they try to access an internal
  page, **then** they are redirected to the sign-in screen.

### US3 — Task creation and assignment (High)
- **Given** a Project Manager in a project, **when** they create a task with a
  title and owner, **then** the task is saved with an initial status of "To do".
  *(Release 1: 0..N assignees, all members of the project; priority defaults to
  Medium.)*
- **Given** a task form, **when** the title is empty, **then** the task is not
  created and a required-field warning is shown.
- **Given** a task assigned to a member, **when** that member opens their list,
  **then** the task appears associated with them.
- *(release 1)* **Given** a task, **when** a Project Manager assigns a user who
  is already assigned or is not a project member, **then** the assignment is
  rejected and no duplicate is stored.

### US4 — Changing task status (High)
- **Given** a Member responsible for a "To do" task, **when** they change it to
  "In progress", **then** the new status is saved and visible to the team.
- *(Deferred with FR10.)* **Given** a status change, **when** it is
  completed, **then** the history records the previous status, the new
  status, the author, and the date.
- **Given** a Member who is neither the owner nor a manager of the task,
  **when** they try to change the status, **then** the action is blocked.

## Relevant exceptional cases

1. *(Deferred with deadlines.)* **Deadline in the past:** when setting a deadline earlier than the current
   date, the system warns the user and asks for confirmation before saving.
2. **Removing a member with assigned tasks:** when a member is removed from a
   project, their tasks become unassigned and are flagged for reassignment
   instead of being deleted. *Release 1 (N:M assignees):* only that member's
   assignments in the project are removed; tasks and comments are kept and
   tasks left without assignees show as unassigned.
3. **Concurrent editing:** if two users edit the same task at the same time, the
   second save is rejected and the user is prompted to reload the latest version
   before retrying, so no changes are silently overwritten.
4. **Project with no tasks:** the progress of a project with no tasks is shown
   as 0% (or "no tasks"), avoiding division by zero in the calculation.
