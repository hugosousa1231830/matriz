import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { ApiError } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { POLL_INTERVAL_MS } from '../api/client'
import { getProject } from '../api/projects'
import {
  deleteTask,
  errorMessage,
  getTask,
  invalidateTaskViews,
  isVersionConflict,
  updateTask,
  type Task,
  type TaskChanges,
} from '../api/tasks'
import { AssigneesPanel } from '../components/AssigneesPanel'
import { PriorityBadge, StatusBadge } from '../components/Badges'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorMessage, Loading } from '../components/Feedback'
import { CommentsPanel } from '../components/CommentsPanel'
import { TaskForm, type TaskValues } from '../components/TaskForm'
import { useDocumentTitle, useFlash } from '../hooks'

/** Only the fields the user changed from the task as it was when editing started. */
function changesFrom(task: Task, values: TaskValues): TaskChanges {
  const changes: TaskChanges = {}
  if (values.title.trim() !== task.title) changes.title = values.title
  if ((values.description.trim() || null) !== (task.description ?? null)) changes.description = values.description
  if (values.status !== task.status) changes.status = values.status
  if (values.priority !== task.priority) changes.priority = values.priority
  return changes
}

/**
 * One task: details, edit (Managers, Administrators and its assignees), delete
 * with confirmation and assignees (Managers), comments (any member).
 */
export function TaskDetailPage() {
  const { taskId = '' } = useParams()
  const navigate = useNavigate()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const flash = useFlash()
  // The task as it was when editing started; null when not editing.
  const [base, setBase] = useState<Task | null>(null)
  // The version the edit applies to: the one read when editing started, or the one the user reloaded.
  // Background refreshes (polling) do not change it, so a concurrent change still answers 409.
  const [editVersion, setEditVersion] = useState(0)
  const [reloaded, setReloaded] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const task = useQuery({ queryKey: ['task', taskId], queryFn: () => getTask(taskId), refetchInterval: POLL_INTERVAL_MS })
  useDocumentTitle(task.data?.title ?? 'Task')
  const projectId = task.data?.projectId ?? ''
  const project = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => getProject(projectId),
    enabled: Boolean(projectId),
  })

  const remove = useMutation({
    mutationFn: () => deleteTask(taskId),
    onSuccess: async () => {
      const title = task.data?.title
      queryClient.removeQueries({ queryKey: ['task', taskId] })
      await invalidateTaskViews(queryClient, projectId)
      await navigate(`/projects/${projectId}`)
      flash(`Task “${title}” deleted.`)
    },
  })

  if (task.isPending) {
    return <Loading>Loading task…</Loading>
  }
  if (task.isError) {
    const notFound = task.error instanceof ApiError && task.error.status === 404
    return (
      <>
        <ErrorMessage onRetry={notFound ? undefined : () => task.refetch()} retrying={task.isFetching}>
          {errorMessage(task.error)}
        </ErrorMessage>
        <Link to="/projects">Back to projects</Link>
      </>
    )
  }

  const t = task.data
  const archived = project.data?.status === 'ARCHIVED'
  const isManager = project.data?.myRole === 'MANAGER' || Boolean(user?.isAdmin)
  const isAssignee = t.assignees.some((assignee) => assignee.id === user?.id)
  const canEdit = Boolean(project.data) && !archived && (isManager || isAssignee)
  const canDelete = Boolean(project.data) && !archived && isManager
  // Anyone who can read the task is a member or an Administrator.
  const canComment = Boolean(project.data) && !archived

  async function save(values: TaskValues) {
    setReloaded(false)
    const changes = changesFrom(base ?? t, values)
    if (Object.keys(changes).length > 0) {
      const updated = await updateTask(taskId, editVersion, changes)
      queryClient.setQueryData(['task', taskId], updated)
      await invalidateTaskViews(queryClient, projectId)
      flash('Task saved.')
    }
    setBase(null)
  }

  async function reload() {
    await queryClient.refetchQueries({ queryKey: ['task', taskId] })
    await invalidateTaskViews(queryClient, projectId)
    // After a reload the user's changes apply on top of the latest version.
    setEditVersion(queryClient.getQueryData<Task>(['task', taskId])?.version ?? t.version)
    setReloaded(true)
  }

  function startEditing() {
    setBase(t)
    setEditVersion(t.version)
  }

  const facts = (
    <dl className="facts facts-inline">
      <div>
        <dt>Status</dt>
        <dd>
          <StatusBadge status={t.status} />
        </dd>
      </div>
      <div>
        <dt>Priority</dt>
        <dd>
          <PriorityBadge priority={t.priority} />
        </dd>
      </div>
      <div>
        <dt>Created by</dt>
        <dd>
          {t.createdBy.name} on {new Date(t.createdAt).toLocaleString()}
        </dd>
      </div>
      <div>
        <dt>Last updated</dt>
        <dd>{new Date(t.updatedAt).toLocaleString()}</dd>
      </div>
    </dl>
  )

  return (
    <>
      <Breadcrumbs
        items={[
          { label: 'Projects', to: '/projects' },
          { label: project.data?.name ?? 'Project', to: `/projects/${t.projectId}` },
          { label: t.title },
        ]}
      />
      <div className="panel panel-accent task-header">
        <div className="task-header-top">
          <h1>{t.title}</h1>
          {!base && (canEdit || canDelete) && (
            <div className="actions">
              {canEdit && (
                <button type="button" className="secondary" onClick={startEditing}>
                  Edit
                </button>
              )}
              {canDelete && (
                <button type="button" className="danger" onClick={() => setConfirmDelete(true)}>
                  Delete
                </button>
              )}
            </div>
          )}
        </div>
        {facts}
      </div>

      {confirmDelete && (
        <ConfirmDialog
          title="Delete task?"
          confirmLabel="Confirm delete"
          pendingLabel="Deleting…"
          pending={remove.isPending}
          error={remove.isError && <ErrorMessage>{errorMessage(remove.error)}</ErrorMessage>}
          onConfirm={() => remove.mutate()}
          onCancel={() => {
            setConfirmDelete(false)
            remove.reset()
          }}
        >
          <p>Delete “{t.title}”? Its assignments and comments are deleted too. This cannot be undone.</p>
        </ConfirmDialog>
      )}
      {archived && <p className="notice">This project is archived and read-only.</p>}

      <div className="task-layout">
        <div className="task-main">
          {base && (
            <section aria-label="Edit task" className="panel panel-accent">
              <h2>Edit task</h2>
              {reloaded && (
                <p className="notice">
                  The latest version is loaded in the task details. Your edits are kept; save again to apply them.
                </p>
              )}
              <TaskForm
                idPrefix="edit-task"
                submitLabel="Save"
                pendingLabel="Saving…"
                withStatus
                initial={{
                  title: base.title,
                  description: base.description ?? '',
                  status: base.status,
                  priority: base.priority,
                }}
                onSubmit={save}
                onCancel={() => {
                  setBase(null)
                  setReloaded(false)
                }}
                errorAction={(error) =>
                  isVersionConflict(error) && (
                    <button type="button" onClick={reload}>
                      Reload task
                    </button>
                  )
                }
              />
            </section>
          )}
          <section aria-labelledby="task-details-title" className="panel">
            <h2 id="task-details-title">{base ? 'Latest saved details' : 'Details'}</h2>
            {t.description ? (
              <p className="task-description">{t.description}</p>
            ) : (
              <p className="muted">No description.</p>
            )}
          </section>
        </div>
        <div className="task-side">
          <AssigneesPanel task={t} canManage={canDelete} />
          <CommentsPanel taskId={t.id} canComment={canComment} />
        </div>
      </div>
    </>
  )
}
