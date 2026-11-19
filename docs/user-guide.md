# Matriz user guide

Step-by-step instructions for every feature of Matriz release 1, from signing
in to commenting on tasks. Each step says **what you do** and **what it does**.

## Contents

1. [Before you start](#1-before-you-start)
2. [Roles and what they allow](#2-roles-and-what-they-allow)
3. [Sign in and sign out](#3-sign-in-and-sign-out)
4. [Navigate the application](#4-navigate-the-application)
5. [Dashboard](#5-dashboard)
6. [Projects list](#6-projects-list)
7. [Create a project](#7-create-a-project)
8. [Project page](#8-project-page)
9. [Edit, archive and unarchive a project](#9-edit-archive-and-unarchive-a-project)
10. [Members](#10-members)
11. [Tasks list](#11-tasks-list)
12. [Create a task](#12-create-a-task)
13. [Task page: view, edit and delete](#13-task-page-view-edit-and-delete)
14. [Assignees](#14-assignees)
15. [Comments](#15-comments)
16. [Messages, empty states and automatic refresh](#16-messages-empty-states-and-automatic-refresh)
17. [Guided tour with the demo accounts](#17-guided-tour-with-the-demo-accounts)

---

## 1. Before you start

1. **Start the application.** From the repository root run
   `docker compose up -d --build` (see the README section *Run with Docker
   Compose*). This starts the database, the backend and the web
   application. Check logs with `docker compose logs -f backend frontend`.
2. **Open the web application** at <http://localhost:5173> (or the port set in
   `MATRIZ_WEB_PORT`).
3. **Pick a demo account.** With `MATRIZ_SEED_DEMO=true` the backend creates
   these fictitious accounts on first start. All use the password set in
   `MATRIZ_DEMO_PASSWORD` (`MatrizDemo#2026` in `.env.example`).

| Email | Name | What the account can see |
|-------|------|--------------------------|
| `ana@matriz.test` | Ana Costa | Manager (and owner) of *Website Redesign* |
| `rui@matriz.test` | Rui Pereira | Member of *Website Redesign*; assignee of some of its tasks |
| `marta@matriz.test` | Marta Silva | Manager of *Mobile App Launch* and of the archived *Legacy Intranet*; no access to *Website Redesign* |
| `vitor@matriz.test` | Vitor Almeida | Administrator: can see and manage every project |
| `joao@matriz.test` | João Ferreira | No projects yet (shows the empty states) |

> Type the password manually if sign-in fails: browser autofill or a keyboard
> layout that changes `#` are common causes. After 5 wrong passwords an account
> is locked for 15 minutes (see [section 3](#3-sign-in-and-sign-out)).

## 2. Roles and what they allow

Roles apply **per project**. A user can be Manager of one project and Member of
another. Administrators have Manager rights on every project.

| Action | Outsider | Member | Assignee of the task | Manager / Administrator |
|--------|:--------:|:------:|:--------------------:|:-----------------------:|
| See the project, its tasks and comments | — | ✔ | ✔ | ✔ |
| Comment on a task | — | ✔ | ✔ | ✔ |
| Change status, title, description, priority of a task | — | — | ✔ (own tasks) | ✔ (any task) |
| Create and delete tasks, add/remove assignees | — | — | — | ✔ |
| Edit, archive, unarchive the project | — | — | — | ✔ |
| Add members, change roles, remove members | — | — | — | ✔ |

- **Any signed-in user can create a project** and becomes its **owner** and
  Manager. The owner cannot be removed or demoted.
- **Outsiders** (users who are not members) cannot see that a project exists:
  opening its address shows "not found".
- **Archived projects are read-only** for everyone, including Managers, until
  they are unarchived.

## 3. Sign in and sign out

### Sign in

1. Open <http://localhost:5173>. If you are not signed in you are taken to the
   **Sign in** page.
2. Type your address in **Email** and your password in **Password**.
3. Click **Sign in** (the button shows "Signing in…" while waiting).

**What it does:** the server checks your password and starts a session (a
secure cookie). You are taken to the **Dashboard**, or back to the page you
originally tried to open.

**Messages you may see:**

| Message | Meaning / what to do |
|---------|----------------------|
| "Email is required." / "Enter a valid email address." | Fill in a valid email. |
| "Password is required." / "Password must have at most 128 characters." | Fill in the password (max. 128 characters). |
| "Invalid email or password." | Wrong email or password (the message is the same on purpose, so it does not reveal which accounts exist). The password field is cleared. Locked or deactivated accounts also show this message. |
| "Too many sign-in attempts. Please wait a minute and try again." | More than 20 attempts per minute from your address; wait a minute. |
| "Cannot reach the server…" | The backend is not running or not reachable; check `docker compose ps`. |

**Account lockout:** 5 consecutive wrong passwords lock the account for
15 minutes. A successful sign-in resets the counter.

### Session expiry

Sessions end after 30 minutes of inactivity, and at most 8 hours after
signing in. When that happens, the next action
sends you to the sign-in page with **"Your session has expired. Please sign in
again."** After signing in you return to where you were.

### Sign out

1. Click **Sign out** at the top right (shows "Signing out…").

**What it does:** ends the session on the server and returns you to the
**Sign in** page. Pressing the browser's Back button does not show protected
data again.

## 4. Navigate the application

The header is on every page once you are signed in:

- **Skip to main content** (visible when tabbing with the keyboard) jumps past
  the header.
- **Matriz** (brand) and **Dashboard** go to the dashboard.
- **Projects** goes to the projects list.
- Your **name** is shown, plus an **Administrator** badge for administrators.
- **Sign out** ends the session.

Other elements:

- **Breadcrumbs** (e.g. *Projects › Website Redesign › Define site map*) on
  project and task pages; click a part to go back up.
- **Success messages** (e.g. "Project “X” created.") appear at the top and
  disappear after 6 seconds; click **Dismiss** to close them earlier.
- **Pagination**: long lists show 20 items per page with **Previous**,
  **Next** and "Page X of Y (N items)". It is hidden when everything fits on
  one page.
- An unknown address shows **Page not found** with links to the dashboard
  and the projects list.

## 5. Dashboard

**Where:** click **Dashboard** (address `/`). This is the first page after
signing in.

**What you see:**

1. **"Signed in as Name (email)"** — confirms which account you are using.
2. **"Tasks in your active projects"** — totals of your active projects'
   tasks by status (**To do**, **In progress**, **Done**) and the
   percentage done.
3. **"Active projects (N)"** — one entry per active project you belong to
   (administrators: every active project), with:
   - the project name (click it to open the project);
   - **"Your role: Manager"**, **"Your role: Member"** or
     **"Administrator access"**;
   - number of members and task progress.

**What it does:** gives a quick summary of your work. The data refreshes by
itself every 60 seconds. Archived projects are not shown here.

**Empty state:** a user without projects (e.g. *joao*) sees "You have no
active projects yet. Create one or ask a Manager to add you." and a
**Go to projects** link.

## 6. Projects list

**Where:** click **Projects** (address `/projects`).

**What you see:** every project you can access, each with its name (and an
**Archived** badge when archived) and "N tasks · P% done · M members".
Click a name to open the project.

### Search projects

1. Type part of a name in **Search by name**.
2. Click **Search** (or press Enter).

**What it does:** shows only projects whose name contains the text, ignoring
upper/lower case. If nothing matches: "No projects match your search." Clear
the box and search again to see all projects.

### Show archived projects

1. Tick **Show archived**.

**What it does:** archived projects are hidden by default; this includes them
in the list (marked **Archived**). Untick to hide them again.

## 7. Create a project

**Who:** any signed-in user.

1. On **Projects**, click **New project**.
2. Fill in **Name** (required, 3–100 characters).
3. Optionally fill in **Description** (up to 1000 characters).
4. Click **Create project** (shows "Creating…"), or **Cancel** to close the
   form without saving.

**What it does:** creates an active project with you as **owner and
Manager**, and shows "Project “Name” created." The project appears in the
list and on your dashboard.

**Validation messages:** "Name is required.", "Name must have between 3 and
100 characters.", "Description must have at most 1000 characters." The form
keeps what you typed so you can correct it.

## 8. Project page

**Where:** click a project name on the dashboard or the projects list
(address `/projects/<id>`).

**What you see, top to bottom:**

1. **Breadcrumbs**, the project **name** and an **Active** or **Archived**
   badge.
2. **Action buttons** (Managers and Administrators only): **Edit project**,
   **Archive** (or **Unarchive**), and a **Members** link that jumps to the
   members section.
3. **"Owner: Name · N members · Your role: …"** and the description.
4. **Task summary**: counts by status and percentage done.
5. **Tasks** section ([section 11](#11-tasks-list)).
6. **Members** section ([section 10](#10-members)).

If the project does not exist or you are not a member you see "Project not
found or you do not have access to it." and a **Back to projects** link.

## 9. Edit, archive and unarchive a project

**Who:** Managers of the project and Administrators.

### Edit

1. On the project page click **Edit project**.
2. Change **Name** and/or **Description** (same limits as when creating).
3. Click **Save** (shows "Saving…"), or **Cancel** to discard.

**What it does:** saves the changes and shows "Project updated."

### Archive

1. Click **Archive**.
2. Read the dialog **"Archive project?"**: "Archive “X”? It becomes read-only
   and is hidden from the project list. Tasks, members and comments are kept,
   and it can be unarchived later."
3. Click **Confirm archive** (shows "Archiving…"), or **Cancel**.

**What it does:** the project becomes **read-only** and is hidden from the
projects list (unless **Show archived** is ticked) and from the dashboard.
Nothing is deleted. The page shows "This project is archived and read-only."
and all editing controls (new task, status changes, members, comments)
disappear for everyone.

### Unarchive

1. Open the archived project (tick **Show archived** on **Projects** to find
   it).
2. Click **Unarchive**.

**What it does:** the project is active again and can be edited.

> Projects are archived, not deleted, so their history is never lost.

## 10. Members

**Where:** the **Members** section of a project page.

**What everyone sees:** each member's name, email and role, an **Owner** badge
on the owner, and a **Deactivated** badge on accounts that were deactivated.

### Add a member (Managers and Administrators)

1. Under **Add a member**, type at least 2 characters of a name or email in
   **Search users by name or email** (max. 100), or leave it empty to list
   everyone who can be added.
2. Choose the role in the select next to it: **Member** (default) or
   **Manager**.
3. Click **Search**.
4. In the results, click **Add Name** next to the person.

**What it does:** the person becomes a member with the chosen role and can now
see the project. The message "Name added as Member." (or Manager) appears.
Only active users who are not members yet are listed, in alphabetical order
and at most 20 (narrow the search to find others); otherwise you see "No
users found who are not members yet."

### Change a member's role

1. In the member's row, choose **Member** or **Manager** in the **Role of
   Name** select.

**What it does:** changes their permissions immediately (see
[section 2](#2-roles-and-what-they-allow)). The owner's role cannot be
changed.

### Remove a member

1. Click **Remove Name** in the member's row.
2. Read the dialog **"Remove member?"**: "Remove Name from the project? Their
   task assignments in this project are removed too."
3. Click **Confirm removal** (shows "Removing…"), or **Cancel**.

**What it does:** the person loses access to the project (it becomes "not
found" for them) and is unassigned from all its tasks. Their comments are kept.
The message "Name removed from the project." appears. The owner cannot be
removed.

Members and outsiders see roles as plain text, without these controls.

## 11. Tasks list

**Where:** the **Tasks** section of a project page.

**What you see:** a board with three columns, **To do**, **In progress** and
**Done**, each with its number of tasks. Each task is a card with its title,
a **Priority** badge (Low / Medium / High), the assignees' initials (or
"Unassigned") and, for users who may edit it, a status select. Click anywhere
on a card to open the task. Each column shows 20 tasks per page with its own
page controls; newest first by default.

### Filter tasks

The **Filters** bar above the board holds **Status**, **Priority**,
**Assignee** and **Sort by**.

1. Open the **Status** dropdown and tick one or more of **To do**,
   **In progress**, **Done**.
2. Open the **Priority** dropdown and tick one or more of **Low**,
   **Medium**, **High**.
3. In **Assignee**, choose **Anyone**, **Me**, **Unassigned**, or a member's
   name (deactivated members are marked "(deactivated)").
4. Click **Clear filters** to remove all filters.

The **Status** and **Priority** dropdowns show **Any** when nothing is
ticked, the value's name when one is ticked, or how many are ticked (for
example "2 selected"). Press **Escape** or click outside to close a dropdown.

**What it does:** the board updates immediately to show only matching tasks
(ticking several values in one group shows tasks matching any of them). Ticking
statuses shows only those columns; with none ticked all three are shown. A
column without matching tasks shows "No matching tasks."; if no column has any:
"No tasks match these filters."

### Sort tasks

1. Choose an option in **Sort by**: **Newest first**, **Oldest first**,
   **Priority (high to low)**, **Priority (low to high)**, **Status (to do
   first)**, **Status (done first)**, **Title (A–Z)**, **Title (Z–A)**.

**What it does:** reorders the tasks in every column and returns to page 1.

### Change a task's status from the list

**Who:** Managers and Administrators (any task) and the task's assignees
(their own tasks), on active projects.

1. On the task's card, choose **To do**, **In progress** or **Done** in its
   status select.

**What it does:** saves the new status at once; the card moves to the matching
column and the task summary and dashboard totals update. Users who cannot edit
the task see no select; the column shows the status.

## 12. Create a task

**Who:** Managers and Administrators, on an active project.

1. In the **Tasks** section click **New task**.
2. Fill in **Title** (required, 3–150 characters).
3. Optionally fill in **Description** (up to 2000 characters).
4. Choose **Priority**: **Low**, **Medium** (default) or **High**.
5. Optionally tick one or more people under **Assignees** (only active members
   of the project are listed).
6. Click **Create task** (shows "Creating…"), or **Cancel**.

**What it does:** creates the task with status **To do**, records you as its
creator, assigns the ticked people and shows "Task “Title” created." The task
appears at the top of the **To do** column.

**Validation messages:** "Title is required.", "Title must have between 3 and
150 characters.", "Description must have at most 2000 characters.",
"Assignees must be active members of the project."

## 13. Task page: view, edit and delete

**Where:** click a task card in the tasks list (address `/tasks/<id>`).

**What you see:** breadcrumbs, the title, the description (or "No
description."), **Status**, **Priority**, **Created by** (name and date),
**Last updated**, then the **Assignees** and **Comments** sections.

### Edit a task

**Who:** Managers and Administrators (any task) and the task's assignees, on
active projects.

1. Click **Edit**.
2. Change **Title**, **Description**, **Status** and/or **Priority**.
3. Click **Save** (shows "Saving…"), or **Cancel** to discard.

**What it does:** saves only the fields you changed and shows "Task saved."

**If someone else changed the task meanwhile:** the save is refused with "This
task was changed by someone else. Reload it to see the latest version; your
changes are kept."

1. Click **Reload task**.
2. The latest saved version appears under the form (**Latest saved version**);
   your edits stay in the form with the note "The latest version is loaded
   below the form. Your edits are kept; save again to apply them."
3. Review, adjust if needed, and click **Save** again.

This prevents silently overwriting another person's changes.

### Delete a task

**Who:** Managers and Administrators, on active projects.

1. Click **Delete**.
2. Read the dialog **"Delete task?"**: "Delete “Title”? Its assignments and
   comments are deleted too. This cannot be undone."
3. Click **Confirm delete** (shows "Deleting…"), or **Cancel**.

**What it does:** permanently deletes the task with its assignments and
comments, returns you to the project page and shows "Task “Title” deleted."

## 14. Assignees

**Where:** the **Assignees** section of a task page. A task can have several
assignees, or none ("Unassigned.").

### Add an assignee (Managers and Administrators)

1. In **Add assignee**, choose a member (only active members not yet assigned
   are listed; "No other active members" when there is nobody left).
2. Click **Assign** (shows "Assigning…").

**What it does:** the person is assigned to the task. They can now edit it and
change its status, and it appears for them under the **Me** assignee filter.

### Remove an assignee

1. Click **Remove Name** next to the assignee.

**What it does:** unassigns the person; they keep read and comment access as
project members.

## 15. Comments

**Where:** the **Comments** section at the bottom of a task page.

**What you see:** comments in order, oldest first, each with the author's name,
date and time and the text. 20 per page; "No comments yet." when empty.

### Add a comment

**Who:** any member of the project and Administrators, on active projects.

1. Type in **Add a comment** (up to 2000 characters).
2. Click **Post comment** (shows "Posting…").

**What it does:** adds your comment with your name and the current time, and
shows the last page so you can see it. Comments cannot be edited or deleted
afterwards, so check the text before posting.

**Validation messages:** "Comment is required.", "Comment must have at most
2000 characters."

## 16. Messages, empty states and automatic refresh

- **Loading:** "Loading tasks…", "Loading task…", "Loading comments…" while
  data arrives.
- **Errors** show a message and, when retrying can help, a **Try again**
  button (shows "Retrying…"). Your typed input is kept so you can retry.

| Message | Meaning |
|---------|---------|
| "This project is archived and read-only." | The project was archived; unarchive it to change anything. |
| "You are not allowed to do this." | Your role does not allow the action (see [section 2](#2-roles-and-what-they-allow)). |
| "Not found, or you do not have access to it." / "Task not found or you do not have access to it." | The item does not exist or you are not a member. |
| "This conflicts with a change made by someone else. Reload and try again." | Someone else changed the same data; reload. |
| "The project owner cannot be removed or demoted." | The owner is always a Manager. |
| "This person is already a member of the project." / "This person is already assigned to the task." | Nothing to do; refresh the page. |
| "Only active users can be added as members." | The account was deactivated. |
| "Your request could not be verified. Reload the page and try again." | Security check failed (e.g. old tab); reload the page. |

- **Automatic refresh:** the dashboard, task lists, task pages and comments
  refresh every 60 seconds, so changes made by others appear without reloading.

## 17. Guided tour with the demo accounts

Follow these steps to see every feature and the differences between roles.

1. **Manager (ana)**
   1. Sign in as `ana@matriz.test`. The dashboard shows *Website Redesign* with
      "Your role: Manager".
   2. Open **Projects**, search for `web`, open *Website Redesign*.
   3. Click **New task**, create "Prepare launch checklist" with priority
      **High** and assignee **Rui Pereira**.
   4. Filter the list by **Assignee: Rui Pereira**, then **Clear filters**;
      sort by **Priority (high to low)**.
   5. Open *Design homepage mockups*, read its comments, post a comment.
   6. Under **Add a member**, search `joao`, choose **Member**, click **Add
      João Ferreira**.
   7. Create a project "Test project", then **Archive** it, tick **Show
      archived** to find it, and **Unarchive** it.
   8. **Sign out**.
2. **Member and assignee (rui)**
   1. Sign in as `rui@matriz.test`; open *Website Redesign*.
   2. Note there is no **New task**, **Edit project** or **Archive** button.
   3. Change the status of *Write content for About page* (assigned to Rui) in
      the list. Tasks not assigned to Rui show the status as text.
   4. Open *Prepare launch checklist* and click **Edit**; change the
      description and **Save**.
   5. Post a comment on any task. **Sign out**.
3. **Outsider (marta)**
   1. Sign in as `marta@matriz.test`. *Website Redesign* is not listed.
   2. Paste the address of a *Website Redesign* page used earlier: it shows
      "not found".
   3. Tick **Show archived** on **Projects** to see *Legacy Intranet*; open it
      and note it is read-only. **Sign out**.
4. **Administrator (vitor)**
   1. Sign in as `vitor@matriz.test`. The header shows the **Administrator**
      badge, and the dashboard lists every active project with
      "Administrator access".
   2. Open *Mobile App Launch* and note that Manager actions are available.
      **Sign out**.
5. **New user (joao)**
   1. Sign in as `joao@matriz.test`. If you added him in step 1.6 he sees
      *Website Redesign* as Member; otherwise he sees the empty dashboard
      message and can create his first project.
6. **Concurrent edit (optional)**
   1. Open the same task as *ana* in one browser and as *rui* in a private
      window; click **Edit** in both.
   2. Save in one, then save in the other: the second gets the "changed by
      someone else" message. Click **Reload task**, review and **Save** again.
