import { keepPreviousData, useMutation, useQuery, useQueryClient, type UseQueryResult } from '@tanstack/react-query'
import { useEffect, useRef, useState, type ReactNode } from 'react'
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
  type TaskPage,
  type TaskSort,
  type TaskStatus,
} from '../api/tasks'
import { useFlash } from '../hooks'
import { AvatarStack } from './Avatar'
import { PriorityBadge } from './Badges'
import { EmptyState, ErrorMessage, Loading } from './Feedback'
import { Pagination } from './Pagination'
import { TaskForm } from './TaskForm'
import { usePressNavigate } from './usePressNavigate'

interface Props {
  projectId: string
  /** Managers and Administrators: create tasks and edit any task. */
  isManager: boolean
  archived: boolean
}

type Filters = Omit<TaskListQuery, 'page'>

/** How long the "done" check stays on a task that was just moved to Done. */
const DONE_CHECK_MS = 1500

const FIRST_PAGES: Record<TaskStatus, number> = { TODO: 1, IN_PROGRESS: 1, DONE: 1 }

function toggle<T>(values: T[], value: T): T[] {
  return values.includes(value) ? values.filter((v) => v !== value) : [...values, value]
}

function statusSlug(status: TaskStatus): string {
  return status.toLowerCase().replace('_', '-')
}

/** The tasks of one status column: same priority/assignee/sort filters, its own page. */
function useColumnTasks(projectId: string, filters: Filters, status: TaskStatus, page: number, visible: boolean) {
  const query: TaskListQuery = { ...filters, statuses: [status], page }
  return useQuery({
    queryKey: ['project', projectId, 'tasks', query],
    queryFn: () => listTasks(projectId, query),
    placeholderData: keepPreviousData,
    refetchInterval: POLL_INTERVAL_MS,
    enabled: visible,
  })
}

/** Task board of a project: one column per status, with filters, sort, pagination per column and creation. */
export function TasksPanel({ projectId, isManager, archived }: Props) {
  const queryClient = useQueryClient()
  const flash = useFlash()
  const { user } = useAuth()
  const [filters, setFilters] = useState<Filters>({
    statuses: [],
    priorities: [],
    assigneeId: '',
    sort: '-createdAt',
  })
  const [pages, setPages] = useState(FIRST_PAGES)
  const [creating, setCreating] = useState(false)
  const [justDone, setJustDone] = useState<string | null>(null)
  const doneTimer = useRef<number | undefined>(undefined)
  useEffect(() => () => window.clearTimeout(doneTimer.current), [])

  const visible = filters.statuses.length > 0 ? STATUSES.filter((s) => filters.statuses.includes(s)) : STATUSES
  const columns: Record<TaskStatus, UseQueryResult<TaskPage>> = {
    TODO: useColumnTasks(projectId, filters, 'TODO', pages.TODO, visible.includes('TODO')),
    IN_PROGRESS: useColumnTasks(projectId, filters, 'IN_PROGRESS', pages.IN_PROGRESS, visible.includes('IN_PROGRESS')),
    DONE: useColumnTasks(projectId, filters, 'DONE', pages.DONE, visible.includes('DONE')),
  }
  const members = useQuery({ queryKey: ['project', projectId, 'members'], queryFn: () => listMembers(projectId) })

  const changeStatus = useMutation({
    mutationFn: ({ task, status }: { task: Task; status: TaskStatus }) =>
      updateTask(task.id, task.version, { status }),
    onSuccess: (updated) => {
      queryClient.setQueryData(['task', updated.id], updated)
      if (updated.status === 'DONE') {
        setJustDone(updated.id)
        window.clearTimeout(doneTimer.current)
        doneTimer.current = window.setTimeout(() => setJustDone(null), DONE_CHECK_MS)
      }
    },
    onSettled: () => invalidateTaskViews(queryClient, projectId),
  })

  function update(changes: Partial<Filters>) {
    setFilters((current) => ({ ...current, ...changes }))
    setPages(FIRST_PAGES)
  }

  const canEdit = (task: Task) =>
    !archived && (isManager || task.assignees.some((assignee) => assignee.id === user?.id))
  const filtered = filters.statuses.length > 0 || filters.priorities.length > 0 || filters.assigneeId !== ''
  const allEmpty = visible.every((status) => columns[status].data?.totalItems === 0)

  return (
    <section aria-labelledby="tasks-title" className="tasks-section">
      <div className="page-title">
        <h2 id="tasks-title">Tasks</h2>
        {isManager && !archived && !creating && (
          <button type="button" onClick={() => setCreating(true)}>
            <span aria-hidden="true">+</span>New task
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

      <div className="task-toolbar">
        <fieldset className="chip-group">
          <legend>Status</legend>
          {STATUSES.map((value) => (
            <label key={value} className="chip">
              <input
                type="checkbox"
                checked={filters.statuses.includes(value)}
                onChange={() => update({ statuses: toggle(filters.statuses, value) })}
              />
              {STATUS_LABELS[value]}
            </label>
          ))}
        </fieldset>
        <fieldset className="chip-group">
          <legend>Priority</legend>
          {PRIORITIES.map((value) => (
            <label key={value} className="chip">
              <input
                type="checkbox"
                checked={filters.priorities.includes(value)}
                onChange={() => update({ priorities: toggle(filters.priorities, value) })}
              />
              {PRIORITY_LABELS[value]}
            </label>
          ))}
        </fieldset>
        <span className="toolbar-field">
          <label htmlFor="task-assignee-filter">Assignee</label>
          <select
            id="task-assignee-filter"
            value={filters.assigneeId}
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
        <span className="toolbar-field">
          <label htmlFor="task-sort">Sort by</label>
          <select id="task-sort" value={filters.sort} onChange={(event) => update({ sort: event.target.value as TaskSort })}>
            {Object.entries(SORT_LABELS).map(([value, label]) => (
              <option key={value} value={value}>
                {label}
              </option>
            ))}
          </select>
        </span>
        {filtered && (
          <button
            type="button"
            className="secondary"
            onClick={() => update({ statuses: [], priorities: [], assigneeId: '' })}
          >
            Clear filters
          </button>
        )}
      </div>

      {changeStatus.isError && <ErrorMessage>{errorMessage(changeStatus.error)}</ErrorMessage>}
      {allEmpty ? (
        <EmptyState emoji={filtered ? '🔍' : '🌱'}>{filtered ? 'No tasks match these filters.' : 'No tasks yet.'}</EmptyState>
      ) : (
        <div className="task-board">
          {visible.map((status) => (
            <TaskColumn
              key={status}
              status={status}
              tasks={columns[status]}
              page={pages[status]}
              onPageChange={(next) => setPages((current) => ({ ...current, [status]: next }))}
              filtered={filtered}
              renderTask={(task) => (
                <TaskCard
                  key={task.id}
                  task={task}
                  justDone={task.id === justDone}
                  statusControl={
                    canEdit(task) && (
                      <div className="task-card-status">
                        <label className="visually-hidden" htmlFor={`status-${task.id}`}>
                          Status of {task.title}
                        </label>
                        <select
                          id={`status-${task.id}`}
                          value={task.status}
                          disabled={changeStatus.isPending}
                          onChange={(event) =>
                            changeStatus.mutate({ task, status: event.target.value as TaskStatus })
                          }
                        >
                          {STATUSES.map((value) => (
                            <option key={value} value={value}>
                              {STATUS_LABELS[value]}
                            </option>
                          ))}
                        </select>
                      </div>
                    )
                  }
                />
              )}
            />
          ))}
        </div>
      )}
    </section>
  )
}

interface ColumnProps {
  status: TaskStatus
  tasks: UseQueryResult<TaskPage>
  page: number
  onPageChange: (page: number) => void
  filtered: boolean
  renderTask: (task: Task) => ReactNode
}

function TaskColumn({ status, tasks, page, onPageChange, filtered, renderTask }: ColumnProps) {
  const label = STATUS_LABELS[status]
  const titleId = `tasks-${statusSlug(status)}-title`
  const data = tasks.data
  // A task moved away from the only card on the last page: go back to the last page that still exists.
  const lastPage = data ? Math.max(1, Math.ceil(data.totalItems / TASK_PAGE_SIZE)) : 1
  const pastLastPage = !!data && data.page > 1 && data.items.length === 0 && data.totalItems > 0
  const outOfRange = pastLastPage && data.page === page
  useEffect(() => {
    if (outOfRange) onPageChange(lastPage)
  }, [outOfRange, lastPage, onPageChange])
  return (
    <section aria-labelledby={titleId} className={`task-column column-${statusSlug(status)}`}>
      <div className="task-column-header">
        <span className="column-dot" aria-hidden="true" />
        <h3 id={titleId}>{label}</h3>
        {data && (
          <span className="column-count">
            {data.totalItems}
            <span className="visually-hidden"> {data.totalItems === 1 ? 'task' : 'tasks'}</span>
          </span>
        )}
      </div>
      {(tasks.isPending || pastLastPage) && (
        <Loading skeleton lines={2}>
          Loading {label.toLowerCase()} tasks…
        </Loading>
      )}
      {tasks.isError && (
        <ErrorMessage onRetry={() => tasks.refetch()} retrying={tasks.isFetching}>
          {errorMessage(tasks.error)}
        </ErrorMessage>
      )}
      {data && data.items.length === 0 && !pastLastPage && (
        <p className="column-empty">{filtered ? 'No matching tasks.' : 'No tasks.'}</p>
      )}
      {data && data.items.length > 0 && (
        <ul className="task-cards" aria-label={`${label} tasks`}>
          {data.items.map(renderTask)}
        </ul>
      )}
      {data && !pastLastPage && (
        <Pagination
          label={`${label} pages`}
          page={page}
          pageSize={TASK_PAGE_SIZE}
          totalItems={data.totalItems}
          itemLabel="tasks"
          onChange={onPageChange}
        />
      )}
    </section>
  )
}

interface CardProps {
  task: Task
  justDone: boolean
  statusControl: ReactNode
}

/** A task card: the whole card links to the task; the status control stays usable above the link. */
function TaskCard({ task, justDone, statusControl }: CardProps) {
  const to = `/tasks/${task.id}`
  const { pressed, onClick } = usePressNavigate(to)
  const names = task.assignees.map((assignee) => assignee.name)
  return (
    <li>
      <article
        className={`card card-hover task-card task-card-${statusSlug(task.status)}${pressed ? ' is-pressed' : ''}`}
      >
        <h4 className="task-card-title">
          <Link className="stretched-link" to={to} onClick={onClick}>
            {task.title}
          </Link>
          {justDone && (
            <span className="done-check pop" aria-hidden="true">
              ✓
            </span>
          )}
        </h4>
        <div className="task-card-meta">
          <PriorityBadge priority={task.priority} />
          {names.length > 0 ? (
            <span className="task-card-assignees">
              <AvatarStack names={names} />
              <span className="visually-hidden">Assigned to {names.join(', ')}</span>
            </span>
          ) : (
            <span className="muted">Unassigned</span>
          )}
        </div>
        {statusControl}
      </article>
    </li>
  )
}
