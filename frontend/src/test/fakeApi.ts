import type { CurrentUser } from '../api/auth'

export const ANA: CurrentUser = {
  id: '00000000-0000-4000-8000-000000000001',
  name: 'Ana Costa',
  email: 'ana@matriz.test',
  isAdmin: false,
}

export const VALID_PASSWORD = 'correct-horse'

const CSRF_TOKEN = 'test-csrf-token'

function problem(status: number, code: string, title: string): Response {
  return new Response(JSON.stringify({ type: `/problems/${code.toLowerCase()}`, title, status, code }), {
    status,
    headers: { 'Content-Type': 'application/problem+json' },
  })
}

/** In-memory stand-in for the auth endpoints of the API, used as `fetch`. */
export class FakeApi {
  user: CurrentUser | null = null
  throttled = false
  readonly requests: Request[] = []

  readonly fetch = async (input: RequestInfo | URL): Promise<Response> => {
    const request = input instanceof Request ? input : new Request(input)
    this.requests.push(request)
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
        return problem(404, 'NOT_FOUND', 'Not found')
    }
  }

  calls(method: string, path: string): Request[] {
    return this.requests.filter((r) => r.method === method && new URL(r.url).pathname.endsWith(path))
  }
}
