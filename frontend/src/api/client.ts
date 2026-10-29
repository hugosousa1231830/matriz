import createClient, { type Middleware } from 'openapi-fetch'
import type { paths } from './schema'

/** Base URL of the Matriz API; relative so the browser stays on one origin. */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

/** How often open screens re-read lists and details from the API (no real-time updates in release 1). */
export const POLL_INTERVAL_MS = 60_000

const CSRF_COOKIE = 'XSRF-TOKEN'
const CSRF_HEADER = 'X-XSRF-TOKEN'
const SAFE_METHODS = new Set(['GET', 'HEAD', 'OPTIONS'])

function readCookie(name: string): string | undefined {
  return document.cookie
    .split('; ')
    .find((entry) => entry.startsWith(`${name}=`))
    ?.slice(name.length + 1)
}

/** Returns the CSRF token from its cookie, asking the API to issue one first if needed. */
async function csrfToken(): Promise<string | undefined> {
  const existing = readCookie(CSRF_COOKIE)
  if (existing) {
    return decodeURIComponent(existing)
  }
  await fetch(`${API_BASE_URL}/auth/csrf`, { credentials: 'same-origin' })
  const issued = readCookie(CSRF_COOKIE)
  return issued && decodeURIComponent(issued)
}

type UnauthorizedListener = () => void
const unauthorizedListeners = new Set<UnauthorizedListener>()

/** Notified when the API answers 401 (no session or session expired), except for sign-in itself. */
export function onUnauthorized(listener: UnauthorizedListener): () => void {
  unauthorizedListeners.add(listener)
  return () => unauthorizedListeners.delete(listener)
}

/** Cookie-to-header CSRF protection on unsafe methods, and 401 notification. */
export const sessionMiddleware: Middleware = {
  async onRequest({ request }) {
    if (!SAFE_METHODS.has(request.method)) {
      const token = await csrfToken()
      if (token) {
        request.headers.set(CSRF_HEADER, token)
      }
    }
    return request
  },
  onResponse({ request, response }) {
    if (response.status === 401 && !request.url.endsWith('/auth/sign-in')) {
      unauthorizedListeners.forEach((listener) => listener())
    }
    return response
  },
}

/**
 * Typed client generated from openapi.yaml (`npm run api:generate`). The
 * session cookie is sent by the browser (HttpOnly, same origin).
 */
export const api = createClient<paths>({
  baseUrl: API_BASE_URL,
  credentials: 'same-origin',
  // Resolved on each call so tests can replace the global fetch.
  fetch: (request) => globalThis.fetch(request),
})
api.use(sessionMiddleware)
