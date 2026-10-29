import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { listMembers } from '../api/projects'
import {
  createTask,
  errorMessage,
  invalidateTaskViews,
  listTasks,
  PRIORITIES,
  PRIORITY_LABELS,
  SORT_LABELS,
  STATUSES,
  STATUS_LABELS,
  TASK_PAGE_SIZE,
  updateTask,
  type Task,
  type TaskListQuery,
  type TaskSort,
  type TaskStatus,
} from '../api/tasks'
import { TaskForm } from './TaskForm'

interface Props {
  projectId: string
  /** Managers and Administrators: create tasks and edit any task. */
  isManager: boolean
  archived: boolean
}

function toggle<T>(values: T[], value: T): T[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value]
}

/** Task list of a project with status/priority/assignee filters, sort, pagination and creation. */
export function TasksPanel({ projectId, isManager, archived }: Props) {
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const [query, setQuery] = useState<TaskListQuery>({
    statuses: [],
    priorities: [],
    assigneeId: '',
    sort: '-createdAt',
    page: 1,
  })
  const [creating, setCreating] = useState(false)

  const tasks = useQuery({
    queryKey: ['project', projectId, 'tasks', query],
    queryFn: () => listTasks(projectId, query),
    placeholderData: keepPreviousData,
  })
  const members = useQuery({ queryKey: ['project', projectId, 'members'], queryFn: () => listMembers(projectId) })

  const changeStatus = useMutation({
    mutationFn: ({ task, status }: { task: Task; status: TaskStatus }) =>
      updateTask(task.id, task.version, { status }),
    onSuccess: (updated) => queryClient.setQueryData(['task', updated.id], updated),
    onSettled: () => invalidateTaskViews(queryClient, projectId),
  })

  function update(changes: Partial<TaskListQuery>) {
    setQuery((current) => ({ ...current, page: 1, ...changes }))
  }

  const canEdit = (task: Task) =>
    !archived && (isManager || task.assignees.some((assignee) => assignee.id === user?.id))
  const page = tasks.data
  const pageCount = page ? Math.max(1, Math.ceil(page.totalItems / TASK_PAGE_SIZE)) : 1
  const filtered = query.statuses.length > 0 || query.priorities.length > 0 || query.assigneeId !== ''

  return (
    <section aria-labelledby="tasks-title" className="panel">
      <div className="page-title">
        <h2 id="tasks-title">Tasks</h2>
        {isManager && !archived && !creating && (
          <button type="button" onClick={() => setCreating(true)}>
            New task
          </button>
        )}
      </div>

      {creating && (
        <section aria-label="New task" className="panel">
          <TaskForm
            idPrefix="new-task"
            submitLabel="Create task"
            pendingLabel="Creating…"
            assigneeOptions={members.data?.map((member) => ({ id: member.user.id, name: member.user.name })) ?? []}
            onSubmit={async (values) => {
              await createTask(projectId, values)
              setCreating(false)
              await invalidateTaskViews(queryClient, projectId)
            }}
            onCancel={() => setCreating(false)}
          />
        </section>
      )}

      <div className="task-filters">
        <fieldset>
          <legend>Status</legend>
          {STATUSES.map((value) => (
            <label key={value} className="checkbox">
              <input
                type="checkbox"
                checked={query.statuses.includes(value)}
                onChange={() => update({ statuses: toggle(query.statuses, value) })}
              />
              {STATUS_LABELS[value]}
            </label>
          ))}
        </fieldset>
        <fieldset>
          <legend>Priority</legend>
          {PRIORITIES.map((value) => (
            <label key={value} className="checkbox">
              <input
                type="checkbox"
                checked={query.priorities.includes(value)}
                onChange={() => update({ priorities: toggle(query.priorities, value) })}
              />
              {PRIORITY_LABELS[value]}
            </label>
          ))}
        </fieldset>
        <span>
          <label htmlFor="task-assignee-filter">Assignee</label>
          <select
            id="task-assignee-filter"
            value={query.assigneeId}
            onChange={(event) => update({ assigneeId: event.target.value })}
          >
            <option value="">Anyone</option>
            {user && <option value={user.id}>Me</option>}
            <option value="none">Unassigned</option>
            {members.data
              ?.filter((member) => member.user.id !== user?.id)
              .map((member) => (
                <option key={member.user.id} value={member.user.id}>
                  {member.user.name}
                </option>
              ))}
          </select>
        </span>
        <span>
          <label htmlFor="task-sort">Sort by</label>
          <select
            id="task-sort"
            value={query.sort}
            onChange={(event) => update({ sort: event.target.value as TaskSort })}
          >
            {Object.entries(SORT_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </span>
      </div>

      {tasks.isPending && <p>Loading tasks…</p>}
      {tasks.isError && (
        <p role="alert" className="error">
          {errorMessage(tasks.error)}
        </p>
      )}
      {changeStatus.isError && (
        <p role="alert" className="error">
          {errorMessage(changeStatus.error)}
        </p>
      )}
      {page && page.items.length === 0 && (
        <p>{filtered ? 'No tasks match these filters.' : 'No tasks yet.'}</p>
      )}
      {page && page.items.length > 0 && (
        <ul className="task-list">
          {page.items.map((task) => (
            <li key={task.id}>
              <span>
                <Link to={`/tasks/${task.id}`}>{task.title}</Link>
                <span className={`badge priority-${task.priority.toLowerCase()}`}>
                  {PRIORITY_LABELS[task.priority]}
                </span>
                <span className="muted">
                  {task.assignees.length ? task.assignees.map((a) => a.name).join(', ') : 'Unassigned'}
                </span>
              </span>
              {canEdit(task) ? (
                <span>
                  <label className="visually-hidden" htmlFor={`status-${task.id}`}>
                    Status of {task.title}
                  </label>
                  <select
                    id={`status-${task.id}`}
                    value={task.status}
                    disabled={changeStatus.isPending}
                    onChange={(event) => changeStatus.mutate({ task, status: event.target.value as TaskStatus })}
                  >
                    {STATUSES.map((value) => (
                      <option key={value} value={value}>
                        {STATUS_LABELS[value]}
                      </option>
                    ))}
                  </select>
                </span>
              ) : (
                <span className="muted">{STATUS_LABELS[task.status]}</span>
              )}
            </li>
          ))}
        </ul>
      )}
      {page && pageCount > 1 && (
        <nav aria-label="Task pages" className="pagination">
          <button
            type="button"
            onClick={() => setQuery((current) => ({ ...current, page: current.page - 1 }))}
            disabled={query.page <= 1}
          >
            Previous
          </button>
          <span>
            Page {page.page} of {pageCount} ({page.totalItems} tasks)
          </span>
          <button
            type="button"
            onClick={() => setQuery((current) => ({ ...current, page: current.page + 1 }))}
            disabled={query.page >= pageCount}
          >
            Next
          </button>
        </nav>
      )}
    </section>
  )
}
