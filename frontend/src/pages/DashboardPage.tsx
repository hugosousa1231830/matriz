import { keepPreviousData, useQuery } from '@tanstack/react-query'
import { Link, useSearchParams } from 'react-router'
import { POLL_INTERVAL_MS } from '../api/client'
import { errorMessage, getDashboard, PAGE_SIZE, percentDone, ROLE_LABELS } from '../api/projects'
import { EmptyState, ErrorMessage, Loading } from '../components/Feedback'
import { Pagination } from '../components/Pagination'
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
      <h1>Dashboard</h1>
      {dashboard.isPending && <Loading>Loading dashboard…</Loading>}
      {dashboard.isError && (
        <ErrorMessage onRetry={() => dashboard.refetch()} retrying={dashboard.isFetching}>
          {errorMessage(dashboard.error)}
        </ErrorMessage>
      )}
      {data && (
        <>
          <p className="muted">
            Signed in as {data.user.name} ({data.user.email})
            {data.user.isAdmin && <span className="badge">Administrator</span>}
          </p>

          <section aria-labelledby="totals-title" className="panel">
            <h2 id="totals-title">Tasks in your active projects</h2>
            <TaskSummary counts={data.totals} percent={percentDone(data.totals)} />
          </section>

          <section aria-labelledby="active-projects-title">
            <h2 id="active-projects-title">Active projects ({data.projects.totalItems})</h2>
            {data.projects.totalItems === 0 ? (
              <EmptyState action={<Link to="/projects">Go to projects</Link>}>
                You have no active projects yet. Create one or ask a Manager to add you.
              </EmptyState>
            ) : (
              <ul className="dashboard-projects" aria-label="Active projects">
                {data.projects.items.map((project) => (
                  <li key={project.id} className="panel">
                    <div className="title-line">
                      <h3>
                        <Link to={`/projects/${project.id}`}>{project.name}</Link>
                      </h3>
                      <span className="muted">
                        {project.myRole ? `Your role: ${ROLE_LABELS[project.myRole]}` : 'Administrator access'} ·{' '}
                        {project.memberCount} {project.memberCount === 1 ? 'member' : 'members'}
                      </span>
                    </div>
                    <TaskSummary
                      counts={project.taskCounts} percent={project.progressPercent}
                      label={`Tasks done in ${project.name}`}
                      compact
                    />
                  </li>
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
