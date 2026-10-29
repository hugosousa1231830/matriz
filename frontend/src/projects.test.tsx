import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { ANA, FakeApi, fakeProject, MARTA, RUI } from './test/fakeApi'

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

beforeEach(() => {
  fakeApi = new FakeApi()
  fakeApi.user = ANA
  vi.stubGlobal('fetch', fakeApi.fetch)
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('projects page', () => {
  it('creates a project and shows it in the list', async () => {
    const user = userEvent.setup()
    renderApp('/projects')
    expect(await screen.findByText('No projects yet. Create the first one.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New project' }))
    await user.type(screen.getByLabelText('Name'), '  Garden Plan  ')
    await user.type(screen.getByLabelText('Description'), 'Spring beds.')
    await user.click(screen.getByRole('button', { name: 'Create project' }))

    expect(await screen.findByRole('link', { name: 'Garden Plan' })).toBeInTheDocument()
    const [create] = fakeApi.calls('POST', '/projects')
    expect(await create.json()).toEqual({ name: 'Garden Plan', description: 'Spring beds.' })
    expect(create.headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token')
  })

  it('validates the form before calling the API and keeps the input on server errors', async () => {
    const user = userEvent.setup()
    renderApp('/projects')
    await user.click(await screen.findByRole('button', { name: 'New project' }))

    await user.type(screen.getByLabelText('Name'), '   ')
    await user.click(screen.getByRole('button', { name: 'Create project' }))
    expect(screen.getByText('Name is required.')).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'ab')
    await user.click(screen.getByRole('button', { name: 'Create project' }))
    expect(screen.getByText('Name must have between 3 and 100 characters.')).toBeInTheDocument()
    expect(fakeApi.calls('POST', '/projects')).toHaveLength(0)

    fakeApi.rejectNextCreate = true
    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'Valid name')
    await user.type(screen.getByLabelText('Description'), 'Some text')
    await user.click(screen.getByRole('button', { name: 'Create project' }))

    expect(await screen.findByText('Description rejected by the server.')).toBeInTheDocument()
    expect(screen.getByLabelText('Name')).toHaveValue('Valid name')
    expect(screen.getByLabelText('Description')).toHaveValue('Some text')
  })

  it('searches, shows archived projects on request and paginates', async () => {
    const user = userEvent.setup()
    for (let i = 1; i <= 21; i++) {
      fakeApi.addProject(fakeProject(`Alpha ${String(i).padStart(2, '0')}`))
    }
    fakeApi.addProject(fakeProject('Beta'))
    fakeApi.addProject(fakeProject('Old Alpha', { status: 'ARCHIVED', archivedAt: new Date().toISOString() }))
    renderApp('/projects')

    expect(await screen.findByText('Page 1 of 2 (22 projects)')).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByRole('link', { name: 'Beta' })).toBeInTheDocument()

    await user.type(screen.getByLabelText('Search by name'), 'ALPHA')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    expect(await screen.findByText('Page 1 of 2 (21 projects)')).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Old Alpha' })).not.toBeInTheDocument()

    await user.click(screen.getByLabelText('Show archived'))
    expect(await screen.findByText('Page 1 of 2 (22 projects)')).toBeInTheDocument()
    const list = within(screen.getByRole('list'))
    expect(list.getByRole('link', { name: 'Alpha 01' })).toBeInTheDocument()
    const last = fakeApi.calls('GET', '/projects').at(-1)!
    expect(new URL(last.url).searchParams.get('includeArchived')).toBe('true')
    expect(new URL(last.url).searchParams.get('search')).toBe('ALPHA')
  })
})

describe('project detail page', () => {
  it('shows progress, edits the project and archives it after confirmation', async () => {
    const user = userEvent.setup()
    const project = fakeApi.addProject(
      fakeProject('Website', { taskCounts: { todo: 3, inProgress: 1, done: 1, total: 5 }, progressPercent: 20 }),
    )
    renderApp(`/projects/${project.id}`)

    expect(await screen.findByRole('heading', { name: 'Website' })).toBeInTheDocument()
    expect(screen.getByText(/20% done — 3 to do, 1 in progress, 1 done \(5 tasks\)/)).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Edit project' }))
    await user.clear(screen.getByLabelText('Name'))
    await user.type(screen.getByLabelText('Name'), 'Website Redesign')
    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('heading', { name: 'Website Redesign' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Archive' }))
    expect(screen.getByRole('alertdialog')).toHaveTextContent('becomes read-only')
    expect(fakeApi.calls('POST', '/archive')).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Confirm archive' }))

    expect(await screen.findByText('This project is archived and read-only.')).toBeInTheDocument()
    expect(screen.getByText('Archived')).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit project' })).not.toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Unarchive' }))
    expect(await screen.findByRole('button', { name: 'Edit project' })).toBeInTheDocument()
  })

  it('hides management actions from members', async () => {
    const project = fakeApi.addProject(fakeProject('Mobile', { myRole: 'MEMBER' }), true)
    renderApp(`/projects/${project.id}`)

    expect(await screen.findByRole('heading', { name: 'Mobile' })).toBeInTheDocument()
    expect(await screen.findByText(RUI.email)).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit project' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Archive' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: /Remove/ })).not.toBeInTheDocument()
  })

  it('shows a not-found message for inaccessible projects', async () => {
    renderApp('/projects/00000000-0000-4000-9000-999999999999')

    expect(await screen.findByRole('alert')).toHaveTextContent('Project not found or you do not have access to it.')
  })

  it('lets managers change roles and remove members after confirmation', async () => {
    const user = userEvent.setup()
    const project = fakeApi.addProject(fakeProject('Team'), true)
    renderApp(`/projects/${project.id}`)

    const role = await screen.findByLabelText(`Role of ${RUI.name}`)
    expect(screen.queryByLabelText(`Role of ${ANA.name}`)).not.toBeInTheDocument()
    await user.selectOptions(role, 'MANAGER')
    const [patch] = fakeApi.calls('PATCH', `/members/${RUI.id}`)
    expect(await patch.json()).toEqual({ role: 'MANAGER' })

    await user.click(screen.getByRole('button', { name: `Remove ${RUI.name}` }))
    expect(fakeApi.calls('DELETE', `/members/${RUI.id}`)).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Confirm removal' }))

    expect(await screen.findByRole('heading', { name: 'Members' })).toBeInTheDocument()
    await vi.waitFor(() => expect(screen.queryByText(RUI.email)).not.toBeInTheDocument())
    expect(fakeApi.calls('DELETE', `/members/${RUI.id}`)).toHaveLength(1)
  })

  it('lets managers search users and add them as members', async () => {
    const user = userEvent.setup()
    const project = fakeApi.addProject(fakeProject('Team'), true)
    renderApp(`/projects/${project.id}`)

    const search = await screen.findByLabelText('Search users by name or email')
    await user.type(search, 'm')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    expect(screen.getByText('Search must have between 2 and 100 characters.')).toBeInTheDocument()
    expect(fakeApi.calls('GET', '/member-candidates')).toHaveLength(0)

    await user.type(search, 'ar')
    await user.click(screen.getByRole('button', { name: 'Search' }))
    const results = await screen.findByRole('list', { name: 'Users to add' })
    expect(within(results).getByText(MARTA.name)).toBeInTheDocument()
    expect(new URL(fakeApi.calls('GET', '/member-candidates')[0].url).searchParams.get('search')).toBe('mar')

    await user.selectOptions(screen.getByLabelText('Role of the new member'), 'MANAGER')
    await user.click(within(results).getByRole('button', { name: `Add ${MARTA.name}` }))
    expect(await screen.findByLabelText(`Role of ${MARTA.name}`)).toHaveValue('MANAGER')
    expect(await fakeApi.calls('POST', '/members')[0].json()).toEqual({ userId: MARTA.id, role: 'MANAGER' })
    expect(await screen.findByText('No users found who are not members yet.')).toBeInTheDocument()
  })
})
