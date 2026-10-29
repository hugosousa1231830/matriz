import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router'
import { createProject, errorMessage, listProjects, PAGE_SIZE } from '../api/projects'
import { ProjectForm } from '../components/ProjectForm'

/** Accessible projects: search, "show archived", pagination and the create form. */
export function ProjectsPage() {
  const queryClient = useQueryClient()
  const [params, setParams] = useSearchParams()
  const search = params.get('search') ?? ''
  const includeArchived = params.get('archived') === 'true'
  const page = Math.max(1, Number(params.get('page')) || 1)
  const [searchInput, setSearchInput] = useState(search)
  const [creating, setCreating] = useState(false)

  const projects = useQuery({
    queryKey: ['projects', { search, includeArchived, page }],
    queryFn: () => listProjects({ search, includeArchived, page }),
    placeholderData: keepPreviousData,
  })

  function update(changes: Record<string, string | undefined>) {
    const next = new URLSearchParams(params)
    for (const [key, value] of Object.entries(changes)) {
      if (value) {
        next.set(key, value)
      } else {
        next.delete(key)
      }
    }
    setParams(next)
  }

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    update({ search: searchInput.trim() || undefined, page: undefined })
  }

  async function handleCreate(name: string, description: string) {
    await createProject(name, description)
    await queryClient.invalidateQueries({ queryKey: ['projects'] })
    setCreating(false)
  }

  const data = projects.data
  const lastPage = data ? Math.max(1, Math.ceil(data.totalItems / PAGE_SIZE)) : 1

  return (
    <>
      <div className="page-title">
        <h1>Projects</h1>
        {!creating && (
          <button type="button" onClick={() => setCreating(true)}>
            New project
          </button>
        )}
      </div>

      {creating && (
        <section aria-label="New project" className="panel">
          <h2>New project</h2>
          <ProjectForm
            idPrefix="new-project"
            submitLabel="Create project"
            pendingLabel="Creating…"
            onSubmit={handleCreate}
            onCancel={() => setCreating(false)}
          />
        </section>
      )}

      <form role="search" className="toolbar" onSubmit={handleSearch}>
        <label htmlFor="project-search">Search by name</label>
        <input
          id="project-search"
          type="search"
          value={searchInput}
          maxLength={100}
          onChange={(event) => setSearchInput(event.target.value)}
        />
        <button type="submit">Search</button>
        <label className="checkbox">
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(event) => update({ archived: event.target.checked ? 'true' : undefined, page: undefined })}
          />
          Show archived
        </label>
      </form>

      {projects.isPending && <p>Loading projects…</p>}
      {projects.isError && (
        <p role="alert" className="error">
          {errorMessage(projects.error)}
        </p>
      )}
      {data && data.items.length === 0 && (
        <p>{search ? 'No projects match your search.' : 'No projects yet. Create the first one.'}</p>
      )}
      {data && data.items.length > 0 && (
        <ul className="project-list">
          {data.items.map((project) => (
            <li key={project.id}>
              <Link to={`/projects/${project.id}`}>{project.name}</Link>
              {project.status === 'ARCHIVED' && <span className="badge archived">Archived</span>}
              <span className="muted">
                {project.taskCounts.total} tasks · {project.progressPercent}% done · {project.memberCount}{' '}
                {project.memberCount === 1 ? 'member' : 'members'}
              </span>
            </li>
          ))}
        </ul>
      )}
      {data && data.totalItems > PAGE_SIZE && (
        <nav aria-label="Pagination" className="pagination">
          <button type="button" disabled={page <= 1} onClick={() => update({ page: String(page - 1) })}>
            Previous
          </button>
          <span>
            Page {page} of {lastPage} ({data.totalItems} projects)
          </span>
          <button type="button" disabled={page >= lastPage} onClick={() => update({ page: String(page + 1) })}>
            Next
          </button>
        </nav>
      )}
    </>
  )
}
