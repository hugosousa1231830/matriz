import { keepPreviousData, useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { POLL_INTERVAL_MS } from '../api/client'
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
import { useFlash } from '../hooks'
import { PriorityBadge } from './Badges'
import { EmptyState, ErrorMessage, Loading } from './Feedback'
import { Pagination } from './Pagination'
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
  const flash = useFlash()
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
    refetchInterval: POLL_INTERVAL_MS,
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
            assigneeOptions={
              members.data
                ?.filter((member) => member.active)
                .map((member) => ({ id: member.user.id, name: member.user.name })) ?? []
            }
            assigneeOptionsState={members.isPending ? 'loading' : members.isError ? 'error' : undefined}
            onSubmit={async (values) => {
              const created = await createTask(projectId, values)
              setCreating(false)
              await invalidateTaskViews(queryClient, projectId)
              flash(`Task “${created.title}” created.`)
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
                  {!member.active && ' (deactivated)'}
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
        {filtered && (
          <button type="button" onClick={() => update({ statuses: [], priorities: [], assigneeId: '' })}>
            Clear filters
          </button>
        )}
      </div>

      {tasks.isPending && <Loading>Loading tasks…</Loading>}
      {tasks.isError && (
        <ErrorMessage onRetry={() => tasks.refetch()} retrying={tasks.isFetching}>
          {errorMessage(tasks.error)}
        </ErrorMessage>
      )}
      {changeStatus.isError && <ErrorMessage>{errorMessage(changeStatus.error)}</ErrorMessage>}
      {page && page.items.length === 0 && (
        <EmptyState>{filtered ? 'No tasks match these filters.' : 'No tasks yet.'}</EmptyState>
      )}
      {page && page.items.length > 0 && (
        <table className="task-table">
          <caption className="visually-hidden">Tasks of the project</caption>
          <thead>
            <tr>
              <th scope="col">Task</th>
              <th scope="col">Assignees</th>
              <th scope="col">Status</th>
              <th scope="col">Priority</th>
            </tr>
          </thead>
          <tbody>
            {page.items.map((task) => (
              <tr key={task.id}>
                <td data-label="Task">
                  <Link to={`/tasks/${task.id}`}>{task.title}</Link>
                </td>
                <td data-label="Assignees">
                  {task.assignees.length ? (
                    task.assignees.map((a) => a.name).join(', ')
                  ) : (
                    <span className="muted">Unassigned</span>
                  )}
                </td>
                <td data-label="Status">
                  {canEdit(task) ? (
                    <>
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
                    </>
                  ) : (
                    STATUS_LABELS[task.status]
                  )}
                </td>
                <td data-label="Priority">
                  <PriorityBadge priority={task.priority} />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      )}
      {page && (
        <Pagination
          label="Task pages"
          page={query.page}
          pageSize={TASK_PAGE_SIZE}
          totalItems={page.totalItems}
          itemLabel="tasks"
          onChange={(next) => setQuery((current) => ({ ...current, page: next }))}
        />
      )}
    </section>
  )
}
