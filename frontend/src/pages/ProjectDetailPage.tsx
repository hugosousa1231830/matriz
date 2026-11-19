import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/auth'
import { POLL_INTERVAL_MS } from '../api/client'
import {
  errorMessage,
  getProject,
  invalidateProjectViews,
  listMembers,
  ROLE_LABELS,
  setArchived,
  updateProject,
  type Project,
} from '../api/projects'
import { useAuth } from '../auth/AuthContext'
import { AvatarStack } from '../components/Avatar'
import { ProjectStatusBadge } from '../components/Badges'
import { Breadcrumbs } from '../components/Breadcrumbs'
import { ConfirmDialog } from '../components/ConfirmDialog'
import { ErrorMessage, Loading } from '../components/Feedback'
import { MembersPanel } from '../components/MembersPanel'
import { ProjectForm } from '../components/ProjectForm'
import { TaskSummary } from '../components/TaskSummary'
import { TasksPanel } from '../components/TasksPanel'
import { useDocumentTitle, useFlash } from '../hooks'

/** Project header (details, progress, edit and archive/unarchive by Managers and Administrators), tasks and members. */
export function ProjectDetailPage() {
  const { projectId = '' } = useParams()
  const queryClient = useQueryClient()
  const flash = useFlash()
  const { user } = useAuth()
  const [editing, setEditing] = useState(false)
  const [confirmArchive, setConfirmArchive] = useState(false)

  const project = useQuery({
    queryKey: ['project', projectId],
    queryFn: () => getProject(projectId),
    refetchInterval: POLL_INTERVAL_MS,
  })
  // Same query as the members and tasks panels, so the avatars need no extra request.
  const members = useQuery({
    queryKey: ['project', projectId, 'members'],
    queryFn: () => listMembers(projectId),
    enabled: project.isSuccess,
  })
  useDocumentTitle(project.data?.name ?? 'Project')

  function refresh(updated: Project) {
    queryClient.setQueryData(['project', projectId], updated)
    return invalidateProjectViews(queryClient)
  }

  const archive = useMutation({
    mutationFn: (archived: boolean) => setArchived(projectId, archived),
    onSuccess: async (updated, archived) => {
      setConfirmArchive(false)
      await refresh(updated)
      flash(archived ? `Project “${updated.name}” archived.` : `Project “${updated.name}” unarchived.`)
    },
  })

  if (project.isPending) {
    return <Loading skeleton>Loading project…</Loading>
  }
  if (project.isError) {
    const notFound = project.error instanceof ApiError && project.error.status === 404
    return (
      <>
        <ErrorMessage onRetry={notFound ? undefined : () => project.refetch()} retrying={project.isFetching}>
          {notFound ? 'Project not found or you do not have access to it.' : errorMessage(project.error)}
        </ErrorMessage>
        <Link to="/projects">Back to projects</Link>
      </>
    )
  }

  const p = project.data
  const archived = p.status === 'ARCHIVED'
  const manager = p.myRole === 'MANAGER' || Boolean(user?.isAdmin)
  const yourRole = p.myRole ? ROLE_LABELS[p.myRole] : user?.isAdmin ? 'Administrator' : undefined

  return (
    <>
      <Breadcrumbs items={[{ label: 'Projects', to: '/projects' }, { label: p.name }]} />
      {editing ? (
        <section aria-label="Edit project" className="panel">
          <h1>Edit project</h1>
          <ProjectForm
            idPrefix="edit-project"
            submitLabel="Save"
            pendingLabel="Saving…"
            initialName={p.name}
            initialDescription={p.description ?? ''}
            onSubmit={async (name, description) => {
              await refresh(await updateProject(projectId, name, description))
              setEditing(false)
              flash('Project updated.')
            }}
            onCancel={() => setEditing(false)}
          />
        </section>
      ) : (
        <div className="panel panel-accent project-header">
          <div className="project-header-main">
            <div className="title-line">
              <h1>{p.name}</h1>
              <ProjectStatusBadge archived={archived} />
            </div>
            {p.description && <p className="task-description project-description">{p.description}</p>}
            <p className="muted">
              Owner: {p.owner.name} · {p.memberCount} {p.memberCount === 1 ? 'member' : 'members'}
              {yourRole && ` · Your role: ${yourRole}`}
            </p>
          </div>
          <div className="actions">
            {members.data && (
              <AvatarStack
                names={members.data.filter((member) => member.active).map((member) => member.user.name)}
                max={4}
                size="md"
              />
            )}
            <a className="button secondary" href="#members">
              Members
            </a>
            {manager && !archived && (
              <button type="button" className="secondary" onClick={() => setEditing(true)}>
                Edit project
              </button>
            )}
            {manager &&
              (archived ? (
                <button
                  type="button"
                  className="secondary"
                  onClick={() => archive.mutate(false)}
                  disabled={archive.isPending}
                >
                  {archive.isPending ? 'Unarchiving…' : 'Unarchive'}
                </button>
              ) : (
                <button type="button" className="secondary" onClick={() => setConfirmArchive(true)}>
                  Archive
                </button>
              ))}
          </div>
        </div>
      )}

      {confirmArchive && (
        <ConfirmDialog
          title="Archive project?"
          confirmLabel="Confirm archive"
          pendingLabel="Archiving…"
          pending={archive.isPending}
          error={archive.isError && <ErrorMessage>{errorMessage(archive.error)}</ErrorMessage>}
          onConfirm={() => archive.mutate(true)}
          onCancel={() => {
            setConfirmArchive(false)
            archive.reset()
          }}
        >
          <p>
            Archive “{p.name}”? It becomes read-only and is hidden from the project list. Tasks, members and
            comments are kept, and it can be unarchived later.
          </p>
        </ConfirmDialog>
      )}
      {!confirmArchive && archive.isError && <ErrorMessage>{errorMessage(archive.error)}</ErrorMessage>}
      {archived && <p className="notice">This project is archived and read-only.</p>}

      <TaskSummary counts={p.taskCounts} percent={p.progressPercent} />

      <TasksPanel projectId={projectId} isManager={manager} archived={archived} />
      <MembersPanel projectId={projectId} canManage={manager && !archived} />
    </>
  )
}
