import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { Link, MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { usePressNavigate } from './components/usePressNavigate'
import { ANA, FakeApi, fakeProject, fakeTask, MARTA } from './test/fakeApi'

let fakeApi: FakeApi

function renderApp(path = '/') {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

/** Adds Marta to the project as a deactivated member. */
function addDeactivatedMarta(projectId: string) {
  fakeApi.members.get(projectId)!.push({
    user: MARTA,
    role: 'MEMBER',
    isOwner: false,
    joinedAt: new Date().toISOString(),
    active: false,
  })
}

beforeEach(() => {
  fakeApi = new FakeApi()
  fakeApi.user = ANA
  vi.stubGlobal('fetch', fakeApi.fetch)
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('dashboard', () => {
  it('summarises the active projects with per-status counts and % done', async () => {
    const user = userEvent.setup()
    const garden = fakeApi.addProject(fakeProject('Garden Plan'), true)
    const kitchen = fakeApi.addProject(fakeProject('Kitchen'))
    fakeApi.addProject(fakeProject('Old Shed', { status: 'ARCHIVED' }))
    fakeApi.tasks.push(
      fakeTask(garden.id, 'Dig', { status: 'DONE' }),
      fakeTask(garden.id, 'Plant', { status: 'IN_PROGRESS' }),
      fakeTask(garden.id, 'Water'),
      fakeTask(kitchen.id, 'Paint', { status: 'DONE' }),
    )
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(await screen.findByText(`Signed in as ${ANA.name} (${ANA.email})`)).toBeInTheDocument()
    const totals = screen.getByRole('region', { name: 'Tasks in your active projects' })
    expect(within(totals).getByText('50% done — 1 to do, 1 in progress, 2 done (4 tasks)')).toBeInTheDocument()

    const list = screen.getByRole('list', { name: 'Active projects' })
    expect(within(list).getAllByRole('heading').map((h) => h.textContent)).toEqual(['Garden Plan', 'Kitchen'])
    expect(within(list).getByText(/33% done — 1 to do, 1 in progress, 1 done \(3 tasks\)/)).toBeInTheDocument()
    expect(screen.queryByText('Old Shed')).not.toBeInTheDocument()

    await user.click(within(list).getByRole('link', { name: 'Kitchen' }))
    expect(await screen.findByRole('heading', { name: 'Kitchen' })).toBeInTheDocument()
    expect(screen.getByRole('navigation', { name: 'Breadcrumb' })).toHaveTextContent('Projects')
  })

  it('shows an empty state when there are no active projects', async () => {
    renderApp('/')
    expect(await screen.findByText(/no active projects/i)).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to projects' })).toHaveAttribute('href', '/projects')
  })

  it('reports a server error and recovers on retry', async () => {
    const user = userEvent.setup()
    fakeApi.addProject(fakeProject('Garden Plan'))
    fakeApi.failNextDashboard = 500
    renderApp('/')

    expect(await screen.findByRole('alert')).toHaveTextContent(/The server had a problem/)
    await user.click(screen.getByRole('button', { name: 'Try again' }))
    expect(await screen.findByRole('link', { name: 'Garden Plan' })).toBeInTheDocument()
    expect(screen.queryByRole('alert')).not.toBeInTheDocument()
  })

  it('explains when the server cannot be reached', async () => {
    const realFetch = fakeApi.fetch
    vi.stubGlobal('fetch', (input: RequestInfo | URL) => {
      const url = input instanceof Request ? input.url : String(input)
      return url.includes('/dashboard') ? Promise.reject(new TypeError('Failed to fetch')) : realFetch(input)
    })
    renderApp('/')
    expect(await screen.findByRole('alert')).toHaveTextContent('Cannot reach the server. Check your connection and try again.')
  })
})

function PressCard() {
  const { pressed, onClick } = usePressNavigate('/target')
  return (
    <article data-testid="card" className={pressed ? 'card is-pressed' : 'card'}>
      <Link to="/target" onClick={onClick}>
        Open target
      </Link>
    </article>
  )
}

function renderPressCard() {
  return render(
    <MemoryRouter>
      <Routes>
        <Route path="/" element={<PressCard />} />
        <Route path="/target" element={<h1>Target page</h1>} />
      </Routes>
    </MemoryRouter>,
  )
}

function stubReducedMotion(reduce: boolean) {
  vi.stubGlobal('matchMedia', (query: string) => ({ matches: reduce && query.includes('reduce'), media: query }))
}

describe('pressable cards', () => {
  it('shows the pressed state, then navigates', async () => {
    stubReducedMotion(false)
    const user = userEvent.setup()
    renderPressCard()

    await user.click(screen.getByRole('link', { name: 'Open target' }))
    expect(screen.getByTestId('card')).toHaveClass('is-pressed')
    expect(screen.queryByRole('heading', { name: 'Target page' })).not.toBeInTheDocument()
    expect(await screen.findByRole('heading', { name: 'Target page' })).toBeInTheDocument()
  })

  it('navigates at once when reduced motion is preferred', async () => {
    stubReducedMotion(true)
    const user = userEvent.setup()
    renderPressCard()

    await user.click(screen.getByRole('link', { name: 'Open target' }))
    expect(screen.getByRole('heading', { name: 'Target page' })).toBeInTheDocument()
  })
})

describe('layout', () => {
  it('has main navigation, marks the current page and signs out', async () => {
    const user = userEvent.setup()
    renderApp('/projects')
    const nav = await screen.findByRole('navigation', { name: 'Main' })
    expect(within(nav).getByRole('link', { name: 'Projects' })).toHaveAttribute('aria-current', 'page')
    expect(within(nav).getByRole('link', { name: 'Dashboard' })).not.toHaveAttribute('aria-current')
    expect(screen.getByRole('link', { name: 'Skip to main content' })).toHaveAttribute('href', '#main')

    await user.click(screen.getByRole('button', { name: 'Sign out' }))
    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
  })

  it('shows a not-found page for unknown addresses', async () => {
    renderApp('/no-such-page')
    expect(await screen.findByRole('heading', { name: 'Page not found' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Go to the dashboard' })).toBeInTheDocument()
  })
})

describe('confirmation dialog', () => {
  it('focuses Cancel, closes on Escape without removing, and returns focus', async () => {
    const user = userEvent.setup()
    const project = fakeApi.addProject(fakeProject('Garden Plan'), true)
    renderApp(`/projects/${project.id}`)

    const trigger = await screen.findByRole('button', { name: 'Remove Rui Pereira' })
    await user.click(trigger)
    const dialog = screen.getByRole('alertdialog', { name: 'Remove member?' })
    expect(dialog).toHaveTextContent('Their task assignments in this project are removed too.')
    expect(within(dialog).getByRole('button', { name: 'Cancel' })).toHaveFocus()

    await user.keyboard('{Escape}')
    expect(screen.queryByRole('alertdialog')).not.toBeInTheDocument()
    expect(trigger).toHaveFocus()
    expect(fakeApi.calls('DELETE', `/members/${'00000000-0000-4000-8000-000000000002'}`)).toHaveLength(0)
  })

  it('removes the member on confirmation and confirms it', async () => {
    const user = userEvent.setup()
    const project = fakeApi.addProject(fakeProject('Garden Plan'), true)
    renderApp(`/projects/${project.id}`)

    await user.click(await screen.findByRole('button', { name: 'Remove Rui Pereira' }))
    await user.click(screen.getByRole('button', { name: 'Confirm removal' }))
    expect(await screen.findByText('Rui Pereira removed from the project.')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Remove Rui Pereira' })).not.toBeInTheDocument()
  })
})

describe('deactivated members', () => {
  it('are flagged in the member list and never offered as assignees', async () => {
    const user = userEvent.setup()
    const project = fakeApi.addProject(fakeProject('Garden Plan'), true)
    addDeactivatedMarta(project.id)
    const task = fakeTask(project.id, 'Plant tomatoes')
    fakeApi.tasks.push(task)
    const { unmount } = renderApp(`/projects/${project.id}`)

    const members = await screen.findByRole('region', { name: 'Members' })
    const marta = (await within(members).findByText(MARTA.name)).closest('li')!
    expect(within(marta).getByText('Deactivated')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New task' }))
    const picker = screen.getByRole('group', { name: 'Assignees' })
    expect(within(picker).getByRole('checkbox', { name: 'Rui Pereira' })).toBeInTheDocument()
    expect(within(picker).queryByRole('checkbox', { name: MARTA.name })).not.toBeInTheDocument()
    unmount()

    renderApp(`/tasks/${task.id}`)
    const select = await screen.findByLabelText('Add assignee')
    await within(select).findByRole('option', { name: 'Rui Pereira' })
    expect(within(select).queryByRole('option', { name: MARTA.name })).not.toBeInTheDocument()
  })
})

describe('success feedback', () => {
  it('confirms a created task', async () => {
    const user = userEvent.setup()
    const project = fakeApi.addProject(fakeProject('Garden Plan'), true)
    renderApp(`/projects/${project.id}`)

    await user.click(await screen.findByRole('button', { name: 'New task' }))
    await user.type(screen.getByLabelText('Title'), 'Plant tomatoes')
    await user.click(screen.getByRole('button', { name: 'Create task' }))
    expect(await screen.findByText('Task “Plant tomatoes” created.')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Dismiss' }))
    expect(screen.queryByText('Task “Plant tomatoes” created.')).not.toBeInTheDocument()
  })
})

describe('form validation', () => {
  it('counts characters as code points, like the API', async () => {
    const { validateTask } = await import('./api/tasks')
    const { validateProject } = await import('./api/projects')
    const { validateComment } = await import('./api/comments')
    const emoji = '😀'
    expect(validateTask(emoji.repeat(3), '').title).toBeUndefined()
    expect(validateTask(emoji.repeat(2), '').title).toBeDefined()
    expect(validateTask(emoji.repeat(150), emoji.repeat(2000))).toEqual({})
    expect(validateProject(emoji.repeat(100), emoji.repeat(1000))).toEqual({})
    expect(validateComment(emoji.repeat(2000))).toBeUndefined()
    expect(validateComment(emoji.repeat(2001))).toBeDefined()
  })
})
