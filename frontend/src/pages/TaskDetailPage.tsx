import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { getProject } from '../api/projects'
import {
  deleteTask,
  errorMessage,
  getTask,
  invalidateTaskViews,
  isVersionConflict,
  PRIORITY_LABELS,
  STATUS_LABELS,
  updateTask,
  type Task,
  type TaskChanges,
} from '../api/tasks'
import { AssigneesPanel } from '../components/AssigneesPanel'
import { CommentsPanel } from '../components/CommentsPanel'
import { TaskForm, type TaskValues } from '../components/TaskForm'

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
  // The task as it was when editing started; null when not editing.
  const [base, setBase] = useState<Task | null>(null)
  const [reloaded, setReloaded] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState(false)

  const task = useQuery({ queryKey: ['task', taskId], queryFn: () => getTask(taskId) })
  const projectId = task.data?.projectId ?? ''
  const project = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => getProject(projectId),
    enabled: Boolean(projectId),
  })

  const remove = useMutation({
    mutationFn: () => deleteTask(taskId),
    onSuccess: async () => {
      queryClient.removeQueries({ queryKey: ['task', taskId] })
      await invalidateTaskViews(queryClient, projectId)
      await navigate(`/projects/${projectId}`)
    },
  })

  if (task.isPending) {
    return <p>Loading task…</p>
  }
  if (task.isError) {
    return (
      <>
        <p role="alert" className="error">
          {errorMessage(task.error)}
        </p>
        <Link to="/">Back to projects</Link>
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
    const latest = queryClient.getQueryData<Task>(['task', taskId]) ?? t
    const changes = changesFrom(base ?? t, values)
    if (Object.keys(changes).length > 0) {
      // The latest version read: after a reload the user's changes apply on top of it.
      const updated = await updateTask(taskId, latest.version, changes)
      queryClient.setQueryData(['task', taskId], updated)
      await invalidateTaskViews(queryClient, projectId)
    }
    setBase(null)
  }

  async function reload() {
    await queryClient.refetchQueries({ queryKey: ['task', taskId] })
    await invalidateTaskViews(queryClient, projectId)
    setReloaded(true)
  }

  return (
    <>
      <Link to={`/projects/${t.projectId}`}>← {project.data?.name ?? 'Project'}</Link>
      {base ? (
        <section aria-label="Edit task" className="panel">
          <h1>Edit task</h1>
          {reloaded && (
            <p className="notice">
              The latest version is loaded below the form. Your edits are kept; save again to apply them.
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
      ) : (
        <div className="page-title">
          <h1>{t.title}</h1>
          {(canEdit || canDelete) && (
            <div className="actions">
              {canEdit && (
                <button type="button" onClick={() => setBase(t)}>
                  Edit
                </button>
              )}
              {canDelete && (
                <button type="button" onClick={() => setConfirmDelete(true)} disabled={confirmDelete}>
                  Delete
                </button>
              )}
            </div>
          )}
        </div>
      )}

      {confirmDelete && (
        <div role="alertdialog" aria-labelledby="delete-title" className="panel notice">
          <p id="delete-title">
            Delete “{t.title}”? Its assignments and comments are deleted too. This cannot be undone.
          </p>
          <button type="button" onClick={() => remove.mutate()} disabled={remove.isPending}>
            Confirm delete
          </button>
          <button type="button" onClick={() => setConfirmDelete(false)} disabled={remove.isPending}>
            Cancel
          </button>
        </div>
      )}
      {remove.isError && (
        <p role="alert" className="error">
          {errorMessage(remove.error)}
        </p>
      )}
      {archived && <p className="notice">This project is archived and read-only.</p>}

      {base && (
        <>
          <h2>Latest saved version</h2>
          <p>{t.title}</p>
        </>
      )}
      {t.description ? <p className="task-description">{t.description}</p> : <p className="muted">No description.</p>}
      <dl className="facts">
        <dt>Status</dt>
        <dd>{STATUS_LABELS[t.status]}</dd>
        <dt>Priority</dt>
        <dd>{PRIORITY_LABELS[t.priority]}</dd>
        <dt>Created by</dt>
        <dd>
          {t.createdBy.name} on {new Date(t.createdAt).toLocaleString()}
        </dd>
        <dt>Last updated</dt>
        <dd>{new Date(t.updatedAt).toLocaleString()}</dd>
      </dl>
      <AssigneesPanel task={t} canManage={canDelete} />
      <CommentsPanel taskId={t.id} canComment={canComment} />
    </>
  )
}
