import type { CurrentUser } from '../api/auth'
import type { Project, ProjectMember } from '../api/projects'

export const ANA: CurrentUser = {
  id: '00000000-0000-4000-8000-000000000001',
  name: 'Ana Costa',
  email: 'ana@matriz.test',
  isAdmin: false,
}

export const VALID_PASSWORD = 'correct-horse'

const CSRF_TOKEN = 'test-csrf-token'

export const RUI = { id: '00000000-0000-4000-8000-000000000002', name: 'Rui Pereira', email: 'rui@matriz.test' }

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

/** In-memory stand-in for the auth, project and member endpoints of the API, used as `fetch`. */
export class FakeApi {
  user: CurrentUser | null = null
  throttled = false
  projects: Project[] = []
  members = new Map<string, ProjectMember[]>()
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
    if (request.method !== 'GET' && project.status === 'ARCHIVED' && rest !== '/unarchive' && rest !== '/archive') {
      return problem(409, 'PROJECT_ARCHIVED', 'Project is archived')
    }
    if (key === 'PATCH ') {
      const body = (await request.json()) as { name?: string; description?: string | null }
      if (body.name !== undefined) project.name = body.name.trim()
      if (body.description !== undefined) project.description = body.description?.trim() || null
      return Response.json(this.view(project))
    }
    if (key === 'POST /archive' || key === 'POST /unarchive') {
      const archive = rest === '/archive'
      project.status = archive ? 'ARCHIVED' : 'ACTIVE'
      project.archivedAt = archive ? new Date().toISOString() : null
      return Response.json(this.view(project))
    }
    const member = /^\/members\/([^/]+)$/.exec(rest)
    const list = this.members.get(project.id) ?? []
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

  private view(project: Project): Project {
    return { ...project, memberCount: this.members.get(project.id)?.length ?? 1 }
  }

  calls(method: string, path: string): Request[] {
    return this.requests.filter((r) => r.method === method && new URL(r.url).pathname.endsWith(path))
  }
}
