import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { Link, useParams } from 'react-router'
import { ApiError } from '../api/auth'
import { useAuth } from '../auth/AuthContext'
import { errorMessage, getProject, setArchived, updateProject, type Project } from '../api/projects'
import { MembersPanel } from '../components/MembersPanel'
import { ProjectForm } from '../components/ProjectForm'
import { TasksPanel } from '../components/TasksPanel'

/** Project header (details, progress, edit and archive/unarchive by Managers and Administrators), tasks and members. */
export function ProjectDetailPage() {
  const { projectId = '' } = useParams()
  const queryClient = useQueryClient()
  const { user } = useAuth()
  const [editing, setEditing] = useState(false)
  const [confirmArchive, setConfirmArchive] = useState(false)

  const project = useQuery({ queryKey: ['project', projectId], queryFn: () => getProject(projectId) })

  function refresh(updated: Project) {
    queryClient.setQueryData(['project', projectId], updated)
    return queryClient.invalidateQueries({ queryKey: ['projects'] })
  }

  const archive = useMutation({
    mutationFn: (archived: boolean) => setArchived(projectId, archived),
    onSuccess: async (updated) => {
      setConfirmArchive(false)
      await refresh(updated)
    },
  })

  if (project.isPending) {
    return <p>Loading project…</p>
  }
  if (project.isError) {
    const notFound = project.error instanceof ApiError && project.error.status === 404
    return (
      <>
        <p role="alert" className="error">
          {notFound ? 'Project not found or you do not have access to it.' : errorMessage(project.error)}
        </p>
        <Link to="/">Back to projects</Link>
      </>
    )
  }

  const p = project.data
  const archived = p.status === 'ARCHIVED'
  const manager = p.myRole === 'MANAGER' || Boolean(user?.isAdmin)

  return (
    <>
      <Link to="/">← Projects</Link>
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
            }}
            onCancel={() => setEditing(false)}
          />
        </section>
      ) : (
        <>
          <div className="page-title">
            <h1>
              {p.name}
              {archived && <span className="badge archived">Archived</span>}
            </h1>
            {manager && (
              <div className="actions">
                {!archived && (
                  <button type="button" onClick={() => setEditing(true)}>
                    Edit
                  </button>
                )}
                {archived ? (
                  <button type="button" onClick={() => archive.mutate(false)} disabled={archive.isPending}>
                    Unarchive
                  </button>
                ) : (
                  <button type="button" onClick={() => setConfirmArchive(true)} disabled={confirmArchive}>
                    Archive
                  </button>
                )}
              </div>
            )}
          </div>
          {p.description && <p>{p.description}</p>}
        </>
      )}

      {confirmArchive && (
        <div role="alertdialog" aria-labelledby="archive-title" className="panel notice">
          <p id="archive-title">
            Archive “{p.name}”? It becomes read-only and is hidden from the project list. Tasks, members and
            comments are kept, and it can be unarchived later.
          </p>
          <button type="button" onClick={() => archive.mutate(true)} disabled={archive.isPending}>
            Confirm archive
          </button>
          <button type="button" onClick={() => setConfirmArchive(false)} disabled={archive.isPending}>
            Cancel
          </button>
        </div>
      )}
      {archive.isError && (
        <p role="alert" className="error">
          {errorMessage(archive.error)}
        </p>
      )}
      {archived && <p className="notice">This project is archived and read-only.</p>}

      <dl className="facts">
        <dt>Owner</dt>
        <dd>{p.owner.name}</dd>
        <dt>Progress</dt>
        <dd>
          {p.progressPercent}% done — {p.taskCounts.todo} to do, {p.taskCounts.inProgress} in progress,{' '}
          {p.taskCounts.done} done ({p.taskCounts.total} tasks)
        </dd>
      </dl>

      <TasksPanel projectId={projectId} isManager={manager} archived={archived} />
      <MembersPanel projectId={projectId} canManage={manager && !archived} />
    </>
  )
}
