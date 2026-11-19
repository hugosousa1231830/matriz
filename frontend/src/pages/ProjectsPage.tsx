import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { useSearchParams } from 'react-router'
import { createProject, errorMessage, invalidateProjectViews, listProjects, PAGE_SIZE } from '../api/projects'
import { EmptyState, ErrorMessage, Loading } from '../components/Feedback'
import { Pagination } from '../components/Pagination'
import { ProjectCard } from '../components/ProjectCard'
import { ProjectForm } from '../components/ProjectForm'
import { useDocumentTitle, useFlash } from '../hooks'

/** Accessible projects: search, "show archived", pagination and the create form. */
export function ProjectsPage() {
  useDocumentTitle('Projects')
  const flash = useFlash()
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
    const created = await createProject(name, description)
    await invalidateProjectViews(queryClient)
    setCreating(false)
    flash(`Project “${created.name}” created.`)
  }

  const data = projects.data

  return (
    <>
      <div className="page-title">
        <div>
          <h1>Projects</h1>
          <p className="muted page-subline">Every project you can see, with its progress.</p>
        </div>
        {!creating && (
          <button type="button" onClick={() => setCreating(true)}>
            <span aria-hidden="true">+</span> New project
          </button>
        )}
      </div>

      {creating && (
        <section aria-label="New project" className="panel panel-accent">
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

      <form role="search" className="toolbar search-toolbar" onSubmit={handleSearch}>
        <span className="toolbar-field search-field">
          <label htmlFor="project-search">Search by name</label>
          <input
            id="project-search"
            type="search"
            value={searchInput}
            maxLength={100}
            placeholder="Search projects…"
            onChange={(event) => setSearchInput(event.target.value)}
          />
        </span>
        <button type="submit" className="secondary">
          Search
        </button>
        <label className="chip">
          <input
            type="checkbox"
            checked={includeArchived}
            onChange={(event) => update({ archived: event.target.checked ? 'true' : undefined, page: undefined })}
          />
          Show archived
        </label>
      </form>

      {projects.isPending && <Loading skeleton>Loading projects…</Loading>}
      {projects.isError && (
        <ErrorMessage onRetry={() => projects.refetch()} retrying={projects.isFetching}>
          {errorMessage(projects.error)}
        </ErrorMessage>
      )}
      {data && data.items.length === 0 && (
        <EmptyState emoji={search ? '🔍' : '🌱'}>
          {search ? 'No projects match your search.' : 'No projects yet. Create the first one.'}
        </EmptyState>
      )}
      {data && data.items.length > 0 && (
        <ul className="project-grid" aria-label="Projects">
          {data.items.map((project) => (
            <ProjectCard key={project.id} project={project} headingLevel="h2" detailed />
          ))}
        </ul>
      )}
      {data && (
        <Pagination
          label="Pagination"
          page={page}
          pageSize={PAGE_SIZE}
          totalItems={data.totalItems}
          itemLabel="projects"
          onChange={(next) => update({ page: String(next) })}
        />
      )}
    </>
  )
}
