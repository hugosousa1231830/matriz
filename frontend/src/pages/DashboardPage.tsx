import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { POLL_INTERVAL_MS } from '../api/client'
import { errorMessage, getDashboard, PAGE_SIZE, percentDone } from '../api/projects'
import { EmptyState, ErrorMessage, Loading } from '../components/Feedback'
import { Pagination } from '../components/Pagination'
import { ProjectCard } from '../components/ProjectCard'
import { TaskSummary } from '../components/TaskSummary'
import { useDocumentTitle } from '../hooks'

/** Landing page: the signed-in user, task totals and their accessible active projects with progress. */
export function DashboardPage() {
  useDocumentTitle('Dashboard')
  const [params, setParams] = useSearchParams()
  const page = Math.max(1, Number(params.get('page')) || 1)

  const dashboard = useQuery({
    queryKey: ['dashboard', page],
    queryFn: () => getDashboard(page),
    placeholderData: keepPreviousData,
    refetchInterval: POLL_INTERVAL_MS,
  })
  const data = dashboard.data

  return (
    <>
      <header className="page-hero panel panel-accent">
        <p className="page-eyebrow">
          Welcome back{' '}
          <span aria-hidden="true">👋</span>
        </p>
        <h1>Dashboard</h1>
        {data && (
          <p className="muted page-subline">
            Signed in as {data.user.name} ({data.user.email})
            {data.user.isAdmin && <span className="badge pill">Administrator</span>}
          </p>
        )}
      </header>
      {dashboard.isPending && <Loading skeleton>Loading dashboard…</Loading>}
      {dashboard.isError && (
        <ErrorMessage onRetry={() => dashboard.refetch()} retrying={dashboard.isFetching}>
          {errorMessage(dashboard.error)}
        </ErrorMessage>
      )}
      {data && (
        <>
          <section aria-labelledby="totals-title" className="page-section">
            <h2 id="totals-title">Tasks in your active projects</h2>
            <TaskSummary counts={data.totals} percent={percentDone(data.totals)} />
          </section>

          <section aria-labelledby="active-projects-title" className="page-section">
            <h2 id="active-projects-title">
              Active projects <span className="count-pill">{data.projects.totalItems}</span>
            </h2>
            {data.projects.totalItems === 0 ? (
              <EmptyState
                emoji="🚀"
                action={
                  <Link className="button" to="/projects">
                    Go to projects
                  </Link>
                }
              >
                You have no active projects yet. Create one or ask a Manager to add you.
              </EmptyState>
            ) : (
              <ul className="project-grid" aria-label="Active projects">
                {data.projects.items.map((project) => (
                  <ProjectCard key={project.id} project={project} />
                ))}
              </ul>
            )}
            <Pagination
              label="Project pages"
              page={page}
              pageSize={PAGE_SIZE}
              totalItems={data.projects.totalItems}
              itemLabel="projects"
              onChange={(next) => setParams(next > 1 ? { page: String(next) } : {})}
            />
          </section>
        </>
      )}
    </>
  )
}
