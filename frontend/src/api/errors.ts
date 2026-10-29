import { ApiError } from './auth'

/** True when the request never got an HTTP response (API down, network lost). */
export function isNetworkError(error: unknown): boolean {
  return error instanceof TypeError
}

/**
 * Generic user-facing message for a failed request, by HTTP status. Feature
 * modules map their specific problem codes first and fall back to this.
 */
export function describeError(error: unknown): string {
  if (isNetworkError(error)) {
    return 'Cannot reach the server. Check your connection and try again.'
  }
  if (error instanceof ApiError) {
    if (error.code === 'PROJECT_ARCHIVED') {
      return 'This project is archived and read-only.'
    }
    if (error.code === 'CSRF_INVALID') {
      return 'Your request could not be verified. Reload the page and try again.'
    }
    switch (error.status) {
      case 400:
        return error.fieldErrors[0]?.message ?? 'Please correct the highlighted fields.'
      case 401:
        return 'Your session has expired. Please sign in again.'
      case 403:
        return 'You are not allowed to do this.'
      case 404:
        return 'Not found, or you do not have access to it.'
      case 409:
        return 'This conflicts with a change made by someone else. Reload and try again.'
      case 422:
        return 'This request cannot be completed in the current state.'
      case 429:
        return 'Too many requests. Please wait a moment and try again.'
    }
    if (error.status >= 500) {
      return 'The server had a problem. Please try again later.'
    }
  }
  return 'Something went wrong. Please try again.'
}

/** Whether a failed query is worth retrying automatically (network or server errors, not 4xx). */
export function isRetryable(error: unknown): boolean {
  return !(error instanceof ApiError && error.status < 500)
}
