import type { CurrentUser } from '../api/auth'
import type { Comment } from '../api/comments'
import type { Project, ProjectMember, UserContact } from '../api/projects'
import type { Task } from '../api/tasks'

export const ANA: CurrentUser = {
  id: '00000000-0000-4000-8000-000000000001',
  name: 'Ana Costa',
  email: 'ana@matriz.test',
  isAdmin: false,
}

export const VALID_PASSWORD = 'correct-horse'

const CSRF_TOKEN = 'test-csrf-token'

export const RUI = { id: '00000000-0000-4000-8000-000000000002', name: 'Rui Pereira', email: 'rui@matriz.test' }

export const MARTA = { id: '00000000-0000-4000-8000-000000000003', name: 'Marta Silva', email: 'marta@matriz.test' }

function problem(
  status: number,
  code: string,
  title: string,
  errors?: { field: string; message: string }[],
): Response {
  return new Response(JSON.stringify({ type: `/problems/${code.toLowerCase()}`, title, status, code, errors }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

let nextId = 100

/** A project owned by Ana, as the API would return it. */
export function fakeProject(name: string, overrides: Partial<Project> = {}): Project {
  const now = new Date().toISOString()
  return {
    id: `00000000-0000-4000-9000-${String(nextId++).padStart(12, '0')}`,
    name,
    description: null,
    status: 'ACTIVE',
    owner: { id: ANA.id, name: ANA.name },
    myRole: 'MANAGER',
    memberCount: 1,
    taskCounts: { todo: 0, inProgress: 0, done: 0, total: 0 },
    progressPercent: 0,
    archivedAt: null,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  }
}

const PRIORITY_ORDER = { LOW: 1, MEDIUM: 2, HIGH: 3 }

/** A task of the project created by Ana, as the API would return it. */
export function fakeTask(projectId: string, title: string, overrides: Partial<Task> = {}): Task {
  const now = new Date(Date.now() + nextId).toISOString()
  return {
    id: `00000000-0000-4000-a000-${String(nextId++).padStart(12, '0')}`,
    projectId,
    title,
    description: null,
    status: 'TODO',
    priority: 'MEDIUM',
    assignees: [],
    createdBy: { id: ANA.id, name: ANA.name },
    version: 1,
    createdAt: now,
    updatedAt: now,
    ...overrides,
  }
}

/** In-memory stand-in for the auth, project, member, task and comment endpoints of the API, used as `fetch`. */
export class FakeApi {
  user: CurrentUser | null = null
  throttled = false
  projects: Project[] = []
  members = new Map<string, ProjectMember[]>()
  tasks: Task[] = []
  comments: Comment[] = []
  /** Active users, for member candidates. */
  directory: UserContact[] = [{ ...ANA }, RUI, MARTA]
  /** The next create answers `400` with a server-side field error. */
  rejectNextCreate = false
  readonly requests: Request[] = []

  /** Adds a project with Ana as owner and, optionally, Rui as member. */
  addProject(project: Project, withRui = false): Project {
    this.projects.push(project)
    const joinedAt = project.createdAt
    const list: ProjectMember[] = [{ user: { ...ANA }, role: 'MANAGER', isOwner: true, joinedAt }]
    if (withRui) {
      list.push({ user: RUI, role: 'MEMBER', isOwner: false, joinedAt })
    }
    this.members.set(project.id, list)
    return { ...project, memberCount: list.length }
  }

  readonly fetch = async (input: RequestInfo | URL): Promise<Response> => {
    const request = input instanceof Request ? input : new Request(input)
    // A clone, so tests can still read the body the fake consumes.
    this.requests.push(request.clone())
    const path = new URL(request.url).pathname.replace('/api/v1', '')
    const key = `${request.method} ${path}`

    if (request.method !== 'GET' && request.headers.get('X-XSRF-TOKEN') !== CSRF_TOKEN) {
      return problem(403, 'CSRF_INVALID', 'Invalid CSRF token')
    }
    switch (key) {
      case 'GET /auth/csrf':
        document.cookie = `XSRF-TOKEN=${CSRF_TOKEN}; path=/`
        return new Response(null, { status: 204 })
      case 'GET /auth/me':
        return this.user ? Response.json(this.user) : problem(401, 'UNAUTHENTICATED', 'Authentication required')
      case 'POST /auth/sign-in': {
        if (this.throttled) {
          return problem(429, 'TOO_MANY_ATTEMPTS', 'Too many sign-in attempts')
        }
        const body = (await request.json()) as { email: string; password: string }
        if (body.email !== ANA.email || body.password !== VALID_PASSWORD) {
          return problem(401, 'INVALID_CREDENTIALS', 'Invalid email or password')
        }
        this.user = ANA
        return Response.json(ANA)
      }
      case 'POST /auth/sign-out':
        if (!this.user) {
          return problem(401, 'UNAUTHENTICATED', 'Authentication required')
        }
        this.user = null
        return new Response(null, { status: 204 })
      default:
        if (!this.user) {
          return problem(401, 'UNAUTHENTICATED', 'Authentication required')
        }
        return this.projectRoutes(request, path)
    }
  }

  private async projectRoutes(request: Request, path: string): Promise<Response> {
    const url = new URL(request.url)
    const taskMatch = /^\/tasks\/([^/]+)(\/.*)?$/.exec(path)
    if (taskMatch) {
      return this.taskRoutes(request, taskMatch[1], taskMatch[2] ?? '')
    }
    if (path === '/projects' && request.method === 'GET') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase()
      const includeArchived = url.searchParams.get('includeArchived') === 'true'
      const page = Number(url.searchParams.get('page') ?? 1)
      const pageSize = Number(url.searchParams.get('pageSize') ?? 20)
      const matching = this.projects
        .filter((p) => includeArchived || p.status === 'ACTIVE')
        .filter((p) => p.name.toLowerCase().includes(search))
        .sort((a, b) => a.name.toLowerCase().localeCompare(b.name.toLowerCase()))
      const items = matching.slice((page - 1) * pageSize, page * pageSize).map((p) => this.view(p))
      return Response.json({ items, page, pageSize, totalItems: matching.length })
    }
    if (path === '/projects' && request.method === 'POST') {
      const body = (await request.json()) as { name: string; description?: string | null }
      const name = body.name.trim()
      if (name.length < 3 || name.length > 100) {
        return problem(400, 'VALIDATION_ERROR', 'Invalid request', [
          { field: 'name', message: 'Name must have between 3 and 100 characters.' },
        ])
      }
      if (this.rejectNextCreate) {
        this.rejectNextCreate = false
        return problem(400, 'VALIDATION_ERROR', 'Invalid request', [
          { field: 'description', message: 'Description rejected by the server.' },
        ])
      }
      const project = this.addProject(fakeProject(name, { description: body.description ?? null }))
      return Response.json(project, { status: 201 })
    }
    const match = /^\/projects\/([^/]+)(\/.*)?$/.exec(path)
    const project = match && this.projects.find((p) => p.id === match[1])
    if (!project) {
      return problem(404, 'NOT_FOUND', 'Not found')
    }
    const rest = match[2] ?? ''
    const key = `${request.method} ${rest}`
    if (key === 'GET ') {
      return Response.json(this.view(project))
    }
    if (key === 'GET /members') {
      return Response.json(this.members.get(project.id))
    }
    if (key === 'GET /tasks') {
      return this.listTasks(project, url)
    }
    if (key === 'GET /member-candidates') {
      const search = (url.searchParams.get('search') ?? '').toLowerCase()
      const members = this.members.get(project.id) ?? []
      return Response.json(
        this.directory
          .filter((u) => !members.some((m) => m.user.id === u.id))
          .filter((u) => u.name.toLowerCase().includes(search) || u.email.includes(search)),
      )
    }
    if (request.method !== 'GET' && project.status === 'ARCHIVED' && rest !== '/unarchive' && rest !== '/archive') {
      return problem(409, 'PROJECT_ARCHIVED', 'Project is archived')
    }
    if (key === 'PATCH ') {
      const body = (await request.json()) as { name?: string; description?: string | null }
      if (body.name !== undefined) project.name = body.name.trim()
      if (body.description !== undefined) project.description = body.description?.trim() || null
      return Response.json(this.view(project))
    }
    if (key === 'POST /tasks') {
      if (project.myRole !== 'MANAGER') {
        return problem(403, 'FORBIDDEN', 'Forbidden')
      }
      const body = (await request.json()) as {
        title: string
        description: string | null
        priority: Task['priority']
        assigneeIds?: string[]
      }
      const title = body.title.trim()
      if (title.length < 3 || title.length > 150) {
        return problem(400, 'VALIDATION_ERROR', 'Invalid request', [
          { field: 'title', message: 'Title must have between 3 and 150 characters.' },
        ])
      }
      if (this.rejectNextCreate) {
        this.rejectNextCreate = false
        return problem(400, 'VALIDATION_ERROR', 'Invalid request', [
          { field: 'description', message: 'Description rejected by the server.' },
        ])
      }
      const members = this.members.get(project.id) ?? []
      const assignees = (body.assigneeIds ?? []).map((id) => members.find((m) => m.user.id === id)?.user)
      if (assignees.some((a) => !a)) {
        return problem(422, 'ASSIGNEE_NOT_ELIGIBLE', 'Assignee not eligible')
      }
      const task = fakeTask(project.id, title, {
        description: body.description,
        priority: body.priority,
        assignees: assignees.map((a) => ({ id: a!.id, name: a!.name })).sort((a, b) => a.name.localeCompare(b.name)),
      })
      this.tasks.push(task)
      return Response.json(task, { status: 201 })
    }
    if (key === 'POST /archive' || key === 'POST /unarchive') {
      const archive = rest === '/archive'
      project.status = archive ? 'ARCHIVED' : 'ACTIVE'
      project.archivedAt = archive ? new Date().toISOString() : null
      return Response.json(this.view(project))
    }
    const list = this.members.get(project.id) ?? []
    if (key === 'POST /members') {
      const body = (await request.json()) as { userId: string; role?: ProjectMember['role'] }
      if (list.some((m) => m.user.id === body.userId)) {
        return problem(409, 'ALREADY_MEMBER', 'Already a member')
      }
      const user = this.directory.find((u) => u.id === body.userId)
      if (!user) {
        return problem(422, 'USER_NOT_ELIGIBLE', 'User not eligible')
      }
      const added: ProjectMember = {
        user,
        role: body.role ?? 'MEMBER',
        isOwner: false,
        joinedAt: new Date().toISOString(),
      }
      this.members.set(project.id, [...list, added])
      return Response.json(added, { status: 201 })
    }
    const member = /^\/members\/([^/]+)$/.exec(rest)
    const target = member && list.find((m) => m.user.id === member[1])
    if (!target) {
      return problem(404, 'NOT_FOUND', 'Not found')
    }
    if (target.isOwner) {
      return problem(422, 'OWNER_IMMUTABLE', 'The project owner cannot be removed or demoted')
    }
    if (request.method === 'PATCH') {
      target.role = ((await request.json()) as { role: ProjectMember['role'] }).role
      return Response.json(target)
    }
    if (request.method === 'DELETE') {
      this.members.set(
        project.id,
        list.filter((m) => m !== target),
      )
      return new Response(null, { status: 204 })
    }
    return problem(404, 'NOT_FOUND', 'Not found')
  }

  private listTasks(project: Project, url: URL): Response {
    const values = (name: string) => (url.searchParams.get(name) ?? '').split(',').filter(Boolean)
    const statuses = values('status')
    const priorities = values('priority')
    const assigneeId = url.searchParams.get('assigneeId')
    const sort = url.searchParams.get('sort') ?? '-createdAt'
    const page = Number(url.searchParams.get('page') ?? 1)
    const pageSize = Number(url.searchParams.get('pageSize') ?? 20)
    const direction = sort.startsWith('-') ? -1 : 1
    const field = sort.replace('-', '')
    const matching = this.tasks
      .filter((t) => t.projectId === project.id)
      .filter((t) => statuses.length === 0 || statuses.includes(t.status))
      .filter((t) => priorities.length === 0 || priorities.includes(t.priority))
      .filter((t) =>
        assigneeId === null
          ? true
          : assigneeId === 'none'
            ? t.assignees.length === 0
            : t.assignees.some((a) => a.id === assigneeId),
      )
      .sort((a, b) => {
        if (field === 'title') return direction * a.title.toLowerCase().localeCompare(b.title.toLowerCase())
        if (field === 'priority') return direction * (PRIORITY_ORDER[a.priority] - PRIORITY_ORDER[b.priority])
        return direction * a.createdAt.localeCompare(b.createdAt)
      })
    const items = matching.slice((page - 1) * pageSize, page * pageSize)
    return Response.json({ items, page, pageSize, totalItems: matching.length })
  }

  private async taskRoutes(request: Request, taskId: string, rest: string): Promise<Response> {
    const task = this.tasks.find((t) => t.id === taskId)
    const project = task && this.projects.find((p) => p.id === task.projectId)
    if (!task || !project) {
      return problem(404, 'NOT_FOUND', 'Not found')
    }
    if (rest === '/comments') {
      return this.commentRoutes(request, task, project)
    }
    if (rest.startsWith('/assignees')) {
      return this.assigneeRoutes(request, task, project, rest)
    }
    if (request.method === 'GET') {
      return Response.json(task)
    }
    const manager = project.myRole === 'MANAGER'
    const assignee = task.assignees.some((a) => a.id === this.user?.id)
    if (!manager && !(request.method === 'PATCH' && assignee)) {
      return problem(403, 'FORBIDDEN', 'Forbidden')
    }
    if (project.status === 'ARCHIVED') {
      return problem(409, 'PROJECT_ARCHIVED', 'Project is archived')
    }
    if (request.method === 'DELETE') {
      this.tasks = this.tasks.filter((t) => t !== task)
      return new Response(null, { status: 204 })
    }
    const body = (await request.json()) as Partial<Task> & { version: number }
    if (body.version !== task.version) {
      return problem(409, 'VERSION_CONFLICT', 'Version conflict')
    }
    if (body.title !== undefined) task.title = body.title.trim()
    if (body.description !== undefined) task.description = body.description
    if (body.status !== undefined) task.status = body.status
    if (body.priority !== undefined) task.priority = body.priority
    task.version += 1
    task.updatedAt = new Date().toISOString()
    return Response.json(task)
  }

  private async assigneeRoutes(request: Request, task: Task, project: Project, rest: string): Promise<Response> {
    if (project.myRole !== 'MANAGER') {
      return problem(403, 'FORBIDDEN', 'Forbidden')
    }
    if (project.status === 'ARCHIVED') {
      return problem(409, 'PROJECT_ARCHIVED', 'Project is archived')
    }
    if (request.method === 'POST' && rest === '/assignees') {
      const { userId } = (await request.json()) as { userId: string }
      if (task.assignees.some((a) => a.id === userId)) {
        return problem(409, 'ALREADY_ASSIGNED', 'Already assigned')
      }
      const member = this.members.get(project.id)?.find((m) => m.user.id === userId)
      if (!member) {
        return problem(422, 'ASSIGNEE_NOT_ELIGIBLE', 'Assignee not eligible')
      }
      task.assignees = [...task.assignees, { id: member.user.id, name: member.user.name }].sort((a, b) =>
        a.name.localeCompare(b.name),
      )
      return Response.json(task, { status: 201 })
    }
    const userId = /^\/assignees\/([^/]+)$/.exec(rest)?.[1]
    if (request.method === 'DELETE' && userId && task.assignees.some((a) => a.id === userId)) {
      task.assignees = task.assignees.filter((a) => a.id !== userId)
      return new Response(null, { status: 204 })
    }
    return problem(404, 'NOT_FOUND', 'Not found')
  }

  private async commentRoutes(request: Request, task: Task, project: Project): Promise<Response> {
    if (request.method === 'GET') {
      const url = new URL(request.url)
      const page = Number(url.searchParams.get('page') ?? 1)
      const pageSize = Number(url.searchParams.get('pageSize') ?? 20)
      const all = this.comments.filter((c) => c.taskId === task.id)
      const items = all.slice((page - 1) * pageSize, page * pageSize)
      return Response.json({ items, page, pageSize, totalItems: all.length })
    }
    if (project.status === 'ARCHIVED') {
      return problem(409, 'PROJECT_ARCHIVED', 'Project is archived')
    }
    const { body } = (await request.json()) as { body: string }
    const text = body.trim()
    if (!text || text.length > 2000) {
      return problem(400, 'VALIDATION_ERROR', 'Invalid request', [
        { field: 'body', message: text ? 'Comment must have at most 2000 characters.' : 'Comment is required.' },
      ])
    }
    const comment: Comment = {
      id: `00000000-0000-4000-b000-${String(nextId++).padStart(12, '0')}`,
      taskId: task.id,
      author: { id: this.user!.id, name: this.user!.name },
      body: text,
      createdAt: new Date().toISOString(),
    }
    this.comments.push(comment)
    return Response.json(comment, { status: 201 })
  }

  private view(project: Project): Project {
    const tasks = this.tasks.filter((t) => t.projectId === project.id)
    const count = (status: Task['status']) => tasks.filter((t) => t.status === status).length
    const taskCounts = { todo: count('TODO'), inProgress: count('IN_PROGRESS'), done: count('DONE'), total: tasks.length }
    return {
      ...project,
      memberCount: this.members.get(project.id)?.length ?? 1,
      taskCounts: tasks.length ? taskCounts : project.taskCounts,
      progressPercent: tasks.length ? Math.round((100 * taskCounts.done) / tasks.length) : project.progressPercent,
    }
  }

  calls(method: string, path: string): Request[] {
    return this.requests.filter((r) => r.method === method && new URL(r.url).pathname.endsWith(path))
  }
}
