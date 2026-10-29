import { api } from './client'
import type { components } from './schema'

export type CurrentUser = components['schemas']['CurrentUser']
type Problem = components['schemas']['Problem']

/** An API error response (RFC 9457 problem details). */
export class ApiError extends Error {
  readonly status: number
  readonly code?: Problem['code']
  /** Field-level validation errors (`400`). */
  readonly fieldErrors: NonNullable<Problem['errors']>

  constructor(status: number, problem?: Partial<Problem>) {
    super(problem?.title ?? `Request failed with status ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.code = problem?.code
    this.fieldErrors = problem?.errors ?? []
  }
}

/** The signed-in user, or `null` when there is no valid session. */
export async function fetchCurrentUser(): Promise<CurrentUser | null> {
  const { data, error, response } = await api.GET('/auth/me')
  if (response.status === 401) {
    return null
  }
  if (!data) {
    throw new ApiError(response.status, error)
  }
  return data
}

export async function signIn(email: string, password: string): Promise<CurrentUser> {
  const { data, error, response } = await api.POST('/auth/sign-in', {
    body: { email, password },
    // Added by the session middleware from the XSRF-TOKEN cookie.
    params: { header: { 'X-XSRF-TOKEN': '' } },
  })
  if (!data) {
    throw new ApiError(response.status, error)
  }
  return data
}

export async function signOut(): Promise<void> {
  const { error, response } = await api.POST('/auth/sign-out', {
    params: { header: { 'X-XSRF-TOKEN': '' } },
  })
  // Without a valid session there is nothing left to end.
  if (!response.ok && response.status !== 401) {
    throw new ApiError(response.status, error)
  }
}
