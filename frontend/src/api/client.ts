import createClient from 'openapi-fetch'
import type { paths } from './schema'

/** Base URL of the Matriz API; relative so the browser stays on one origin. */
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL ?? '/api/v1'

/**
 * Typed client generated from openapi.yaml (`npm run api:generate`).
 * Session cookie, CSRF header and 401 handling are added with authentication.
 */
export const api = createClient<paths>({
  baseUrl: API_BASE_URL,
  credentials: 'same-origin',
})
