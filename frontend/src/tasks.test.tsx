import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { render, screen, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import type { Project } from './api/projects'
import { ANA, FakeApi, fakeProject, fakeTask, RUI } from './test/fakeApi'

let fakeApi: FakeApi

function renderApp(path: string) {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

function addProject(overrides: Partial<Project> = {}): Project {
  return fakeApi.addProject(fakeProject('Garden Plan', overrides), true)
}

function lastListQuery(): URLSearchParams {
  const calls = fakeApi.requests.filter((r) => r.method === 'GET' && new URL(r.url).pathname.endsWith('/tasks'))
  return new URL(calls[calls.length - 1].url).searchParams
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

describe('project tasks', () => {
  it('creates a task after validating the form, and refreshes the list and counts', async () => {
    const user = userEvent.setup()
    const project = addProject()
    renderApp(`/projects/${project.id}`)
    expect(await screen.findByText('No tasks yet.')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'New task' }))
    await user.type(screen.getByLabelText('Title'), '   ')
    await user.click(screen.getByRole('button', { name: 'Create task' }))
    expect(screen.getByText('Title is required.')).toBeInTheDocument()
    await user.clear(screen.getByLabelText('Title'))
    await user.type(screen.getByLabelText('Title'), 'ab')
    await user.click(screen.getByRole('button', { name: 'Create task' }))
    expect(screen.getByText('Title must have between 3 and 150 characters.')).toBeInTheDocument()
    expect(fakeApi.calls('POST', '/tasks')).toHaveLength(0)

    fakeApi.rejectNextCreate = true
    await user.clear(screen.getByLabelText('Title'))
    await user.type(screen.getByLabelText('Title'), '  Plant tomatoes  ')
    await user.type(screen.getByLabelText('Description'), 'Two rows.')
    await user.selectOptions(screen.getByLabelText('Priority'), 'HIGH')
    await user.click(screen.getByRole('button', { name: 'Create task' }))
    expect(await screen.findByText('Description rejected by the server.')).toBeInTheDocument()
    expect(screen.getByLabelText('Title')).toHaveValue('  Plant tomatoes  ')

    await user.click(screen.getByRole('button', { name: 'Create task' }))
    expect(await screen.findByRole('link', { name: 'Plant tomatoes' })).toBeInTheDocument()
    expect(await screen.findByText(/1 to do, 0 in progress, 0 done \(1 task\)/)).toBeInTheDocument()
    const create = fakeApi.calls('POST', '/tasks')[1]
    expect(await create.json()).toEqual({
      title: 'Plant tomatoes',
      description: 'Two rows.',
      priority: 'HIGH',
      assigneeIds: [],
    })
    expect(create.headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token')
  })

  it('filters by status, priority and assignee, sorts and paginates', async () => {
    const user = userEvent.setup()
    const project = addProject()
    for (let i = 1; i <= 21; i++) {
      fakeApi.tasks.push(fakeTask(project.id, `Task ${String(i).padStart(2, '0')}`))
    }
    fakeApi.tasks.push(
      fakeTask(project.id, 'Water the beds', { status: 'DONE', priority: 'HIGH', assignees: [RUI] }),
      fakeTask(project.id, 'Buy seeds', { status: 'IN_PROGRESS', priority: 'LOW' }),
    )
    renderApp(`/projects/${project.id}`)

    expect(await screen.findByText('Page 1 of 2 (23 tasks)')).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Buy seeds' })).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Next' }))
    expect(await screen.findByText('Page 2 of 2 (23 tasks)')).toBeInTheDocument()
    expect(lastListQuery().get('page')).toBe('2')

    const status = screen.getByRole('group', { name: 'Status' })
    await user.click(within(status).getByLabelText('Done'))
    await user.click(within(status).getByLabelText('In progress'))
    expect(await screen.findByRole('link', { name: 'Water the beds' })).toBeInTheDocument()
    expect(screen.getByRole('link', { name: 'Buy seeds' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Task 01' })).not.toBeInTheDocument()
    expect(lastListQuery().get('status')).toBe('DONE,IN_PROGRESS')
    expect(lastListQuery().get('page')).toBe('1')

    await user.click(within(screen.getByRole('group', { name: 'Priority' })).getByLabelText('High'))
    expect(await screen.findByRole('link', { name: 'Water the beds' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Buy seeds' })).not.toBeInTheDocument()

    await user.selectOptions(screen.getByLabelText('Assignee'), 'none')
    expect(await screen.findByText('No tasks match these filters.')).toBeInTheDocument()
    expect(lastListQuery().get('assigneeId')).toBe('none')
    await user.selectOptions(screen.getByLabelText('Assignee'), 'Rui Pereira')
    expect(await screen.findByRole('link', { name: 'Water the beds' })).toBeInTheDocument()
    expect(lastListQuery().get('assigneeId')).toBe(RUI.id)

    await user.selectOptions(screen.getByLabelText('Sort by'), 'Title (A–Z)')
    expect(lastListQuery().get('sort')).toBe('title')
  })

  it('changes the status inline and refreshes the project counts', async () => {
    const user = userEvent.setup()
    const project = addProject()
    fakeApi.tasks.push(fakeTask(project.id, 'Plant tomatoes'))
    renderApp(`/projects/${project.id}`)

    await user.selectOptions(await screen.findByLabelText('Status of Plant tomatoes'), 'DONE')

    expect(await screen.findByText(/0 to do, 0 in progress, 1 done \(1 task\)/)).toBeInTheDocument()
    const [patch] = fakeApi.calls('PATCH', fakeApi.tasks[0].id)
    expect(await patch.json()).toEqual({ status: 'DONE', version: 1 })
  })

  it('shows tasks read-only to members who are not assigned', async () => {
    const project = addProject({ myRole: 'MEMBER' })
    fakeApi.tasks.push(
      fakeTask(project.id, 'Not mine'),
      fakeTask(project.id, 'Mine', { assignees: [{ id: ANA.id, name: ANA.name }] }),
    )
    renderApp(`/projects/${project.id}`)

    expect(await screen.findByRole('link', { name: 'Not mine' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'New task' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Status of Not mine')).not.toBeInTheDocument()
    expect(screen.getByLabelText('Status of Mine')).toBeInTheDocument()
  })

  it('hides task changes on archived projects', async () => {
    const project = addProject({ status: 'ARCHIVED' })
    fakeApi.tasks.push(fakeTask(project.id, 'Frozen'))
    renderApp(`/projects/${project.id}`)

    expect(await screen.findByRole('link', { name: 'Frozen' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'New task' })).not.toBeInTheDocument()
    expect(screen.queryByLabelText('Status of Frozen')).not.toBeInTheDocument()
  })
})

describe('task detail page', () => {
  it('edits a task and, after a version conflict, reloads without losing the typed input', async () => {
    const user = userEvent.setup()
    const project = addProject()
    const task = fakeTask(project.id, 'Plant tomatoes', { description: 'Two rows.' })
    fakeApi.tasks.push(task)
    renderApp(`/tasks/${task.id}`)

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    await user.clear(screen.getByLabelText('Title'))
    await user.type(screen.getByLabelText('Title'), 'Plant cherry tomatoes')
    await user.selectOptions(screen.getByLabelText('Status'), 'IN_PROGRESS')
    // Someone else saves first.
    task.priority = 'HIGH'
    task.version = 2

    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByText(/changed by someone else/)).toBeInTheDocument()
    await user.click(screen.getByRole('button', { name: 'Reload task' }))
    expect(await screen.findByText(/The latest version is loaded/)).toBeInTheDocument()
    expect(screen.getByText('High', { selector: 'dd .badge' })).toBeInTheDocument()
    expect(screen.getByLabelText('Title')).toHaveValue('Plant cherry tomatoes')
    expect(screen.getByLabelText('Status')).toHaveValue('IN_PROGRESS')

    await user.click(screen.getByRole('button', { name: 'Save' }))
    expect(await screen.findByRole('heading', { name: 'Plant cherry tomatoes' })).toBeInTheDocument()
    expect(screen.getByText('In progress', { selector: 'dd .badge' })).toBeInTheDocument()
    expect(screen.getByText('High', { selector: 'dd .badge' })).toBeInTheDocument()
    const patches = fakeApi.calls('PATCH', task.id)
    expect(await patches[1].json()).toEqual({
      title: 'Plant cherry tomatoes',
      status: 'IN_PROGRESS',
      version: 2,
    })
  })

  it('validates the edit form before calling the API', async () => {
    const user = userEvent.setup()
    const project = addProject()
    const task = fakeTask(project.id, 'Plant tomatoes')
    fakeApi.tasks.push(task)
    renderApp(`/tasks/${task.id}`)

    await user.click(await screen.findByRole('button', { name: 'Edit' }))
    await user.clear(screen.getByLabelText('Title'))
    await user.type(screen.getByLabelText('Title'), '   ')
    await user.type(screen.getByLabelText('Description'), 'x'.repeat(2001))
    await user.click(screen.getByRole('button', { name: 'Save' }))

    expect(screen.getByText('Title is required.')).toBeInTheDocument()
    expect(screen.getByText('Description must have at most 2000 characters.')).toBeInTheDocument()
    expect(fakeApi.calls('PATCH', task.id)).toHaveLength(0)
  })

  it('deletes a task after confirmation and returns to the project', async () => {
    const user = userEvent.setup()
    const project = addProject()
    const task = fakeTask(project.id, 'Plant tomatoes')
    fakeApi.tasks.push(task, fakeTask(project.id, 'Water the beds'))
    renderApp(`/tasks/${task.id}`)

    await user.click(await screen.findByRole('button', { name: 'Delete' }))
    expect(screen.getByRole('alertdialog')).toHaveTextContent('Its assignments and comments are deleted too.')
    expect(fakeApi.calls('DELETE', task.id)).toHaveLength(0)
    await user.click(screen.getByRole('button', { name: 'Confirm delete' }))

    expect(await screen.findByRole('link', { name: 'Water the beds' })).toBeInTheDocument()
    expect(screen.queryByRole('link', { name: 'Plant tomatoes' })).not.toBeInTheDocument()
    expect(screen.getByRole('heading', { name: 'Garden Plan' })).toBeInTheDocument()
  })

  it('lets an assignee edit but not delete, and keeps other members read-only', async () => {
    const project = addProject({ myRole: 'MEMBER' })
    const mine = fakeTask(project.id, 'Mine', { assignees: [{ id: ANA.id, name: ANA.name }] })
    const other = fakeTask(project.id, 'Not mine')
    fakeApi.tasks.push(mine, other)

    const { unmount } = renderApp(`/tasks/${mine.id}`)
    expect(await screen.findByRole('button', { name: 'Edit' })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
    unmount()

    renderApp(`/tasks/${other.id}`)
    expect(await screen.findByRole('heading', { name: 'Not mine' })).toBeInTheDocument()
    expect(await screen.findByRole('link', { name: /Garden Plan/ })).toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Edit' })).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: 'Delete' })).not.toBeInTheDocument()
  })

  it('reports a task that does not exist or is not visible', async () => {
    renderApp('/tasks/00000000-0000-4000-a000-999999999999')
    expect(await screen.findByRole('alert')).toHaveTextContent('Task not found or you do not have access to it.')
  })
})

describe('task assignees', () => {
  it('assigns members when creating a task', async () => {
    const user = userEvent.setup()
    const project = addProject()
    renderApp(`/projects/${project.id}`)

    await user.click(await screen.findByRole('button', { name: 'New task' }))
    const picker = screen.getByRole('group', { name: 'Assignees' })
    await user.click(await within(picker).findByRole('checkbox', { name: RUI.name }))
    await user.click(within(picker).getByRole('checkbox', { name: ANA.name }))
    await user.type(screen.getByLabelText('Title'), 'Shared chore')
    await user.click(screen.getByRole('button', { name: 'Create task' }))

    expect(await screen.findByRole('link', { name: 'Shared chore' })).toBeInTheDocument()
    const [create] = fakeApi.calls('POST', '/tasks')
    expect(((await create.json()) as { assigneeIds: string[] }).assigneeIds).toEqual([RUI.id, ANA.id])
    expect(fakeApi.tasks[0].assignees.map((a) => a.name)).toEqual([ANA.name, RUI.name])
  })

  it('adds several assignees and removes each one on the task page', async () => {
    const user = userEvent.setup()
    const project = addProject()
    const task = fakeTask(project.id, 'Fix the fence')
    fakeApi.tasks.push(task)
    renderApp(`/tasks/${task.id}`)

    expect(await screen.findByText('Unassigned.')).toBeInTheDocument()
    const select = await screen.findByLabelText('Add assignee')
    await within(select).findByRole('option', { name: RUI.name })
    await user.selectOptions(select, RUI.id)
    await user.click(screen.getByRole('button', { name: 'Assign' }))
    expect(await screen.findByRole('button', { name: `Remove ${RUI.name}` })).toBeInTheDocument()
    // Assigned members are no longer offered: no duplicates.
    expect(within(select).queryByRole('option', { name: RUI.name })).not.toBeInTheDocument()

    await user.selectOptions(select, ANA.id)
    await user.click(screen.getByRole('button', { name: 'Assign' }))
    expect(await screen.findByRole('button', { name: `Remove ${ANA.name}` })).toBeInTheDocument()
    expect(within(select).getByRole('option', { name: 'No other active members' })).toBeInTheDocument()
    expect(await fakeApi.calls('POST', '/assignees')[0].json()).toEqual({ userId: RUI.id })

    await user.click(screen.getByRole('button', { name: `Remove ${RUI.name}` }))
    await vi.waitFor(() =>
      expect(screen.queryByRole('button', { name: `Remove ${RUI.name}` })).not.toBeInTheDocument(),
    )
    await user.click(screen.getByRole('button', { name: `Remove ${ANA.name}` }))
    expect(await screen.findByText('Unassigned.')).toBeInTheDocument()
    expect(fakeApi.tasks[0].assignees).toEqual([])
  })

  it('shows assignees read-only to members and on archived projects', async () => {
    const project = addProject({ myRole: 'MEMBER' })
    const task = fakeTask(project.id, 'Prune roses', { assignees: [{ id: RUI.id, name: RUI.name }] })
    fakeApi.tasks.push(task)
    renderApp(`/tasks/${task.id}`)

    expect(await screen.findByRole('heading', { name: 'Assignees' })).toBeInTheDocument()
    expect(screen.getByText(RUI.name)).toBeInTheDocument()
    await screen.findByRole('link', { name: /Garden Plan/ })
    expect(screen.queryByLabelText('Add assignee')).not.toBeInTheDocument()
    expect(screen.queryByRole('button', { name: `Remove ${RUI.name}` })).not.toBeInTheDocument()
  })
})

describe('task comments', () => {
  it('validates, posts and lists comments with author and date', async () => {
    const user = userEvent.setup()
    const project = addProject({ myRole: 'MEMBER' })
    const task = fakeTask(project.id, 'Order seeds')
    fakeApi.tasks.push(task)
    fakeApi.comments.push({
      id: '00000000-0000-4000-b000-000000000001',
      taskId: task.id,
      author: { id: RUI.id, name: RUI.name },
      body: 'Which ones?',
      createdAt: '2026-01-02T10:00:00Z',
    })
    renderApp(`/tasks/${task.id}`)

    const list = await screen.findByRole('list', { name: 'Comments' })
    expect(within(list).getByText(RUI.name)).toBeInTheDocument()
    expect(within(list).getByText('Which ones?')).toBeInTheDocument()
    expect(within(list).getByText(new Date('2026-01-02T10:00:00Z').toLocaleString())).toBeInTheDocument()

    const textarea = await screen.findByLabelText('Add a comment')
    await user.type(textarea, '   ')
    await user.click(screen.getByRole('button', { name: 'Post comment' }))
    expect(screen.getByText('Comment is required.')).toBeInTheDocument()
    await user.clear(textarea)
    await user.click(textarea)
    await user.paste('x'.repeat(2001))
    await user.click(screen.getByRole('button', { name: 'Post comment' }))
    expect(screen.getByText('Comment must have at most 2000 characters.')).toBeInTheDocument()
    expect(fakeApi.calls('POST', '/comments')).toHaveLength(0)

    await user.clear(textarea)
    await user.type(textarea, '  Tomatoes and basil.  ')
    await user.click(screen.getByRole('button', { name: 'Post comment' }))

    expect(await within(list).findByText('Tomatoes and basil.')).toBeInTheDocument()
    expect(within(list).getByText(ANA.name)).toBeInTheDocument()
    expect(textarea).toHaveValue('')
    expect(await fakeApi.calls('POST', '/comments')[0].json()).toEqual({ body: 'Tomatoes and basil.' })
  })

  it('keeps the typed comment when the server rejects it', async () => {
    const user = userEvent.setup()
    const project = addProject()
    const task = fakeTask(project.id, 'Order seeds')
    fakeApi.tasks.push(task)
    renderApp(`/tasks/${task.id}`)

    const textarea = await screen.findByLabelText('Add a comment')
    await user.type(textarea, 'Too late')
    // Archived by someone else meanwhile.
    fakeApi.projects[0].status = 'ARCHIVED'
    await user.click(screen.getByRole('button', { name: 'Post comment' }))
    expect(await screen.findByText('This project is archived and read-only.', { selector: '.error p' })).toBeInTheDocument()
    expect(textarea).toHaveValue('Too late')
  })

  it('hides the comment form on archived projects but still lists comments', async () => {
    const project = addProject({ status: 'ARCHIVED' })
    const task = fakeTask(project.id, 'Old task')
    fakeApi.tasks.push(task)
    fakeApi.comments.push({
      id: '00000000-0000-4000-b000-000000000002',
      taskId: task.id,
      author: { id: ANA.id, name: ANA.name },
      body: 'Kept for reference.',
      createdAt: '2026-01-02T10:00:00Z',
    })
    renderApp(`/tasks/${task.id}`)

    expect(await screen.findByText('Kept for reference.')).toBeInTheDocument()
    expect(await screen.findByText('This project is archived and read-only.')).toBeInTheDocument()
    expect(screen.queryByLabelText('Add a comment')).not.toBeInTheDocument()
  })
})
