import { api } from './client'
import { ApiError } from './auth'
import { describeError } from './errors'
import { unwrap, XSRF } from './projects'
import type { components } from './schema'

export type Comment = components['schemas']['Comment']
export type CommentPage = components['schemas']['CommentPage']

export const COMMENT_PAGE_SIZE = 20
export const COMMENT_MAX = 2000

/** Same rules as the API (trimmed, 1–2000 characters, not whitespace-only). */
export function validateComment(body: string): string | undefined {
  const trimmed = body.trim()
  if (!trimmed) {
    return 'Comment is required.'
  }
  if (trimmed.length > COMMENT_MAX) {
    return `Comment must have at most ${COMMENT_MAX} characters.`
  }
  return undefined
}

/** User-facing message for a failed comment operation. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'PROJECT_ARCHIVED':
        return 'This project is archived and read-only.'
      case 'FORBIDDEN':
        return 'You are not allowed to do this.'
      case 'NOT_FOUND':
        return 'Task not found or you do not have access to it.'
      case 'VALIDATION_ERROR':
        return error.fieldErrors[0]?.message ?? 'Please correct the comment.'
    }
  }
  return describeError(error)
}

/** Oldest first. */
export async function listComments(taskId: string, page: number): Promise<CommentPage> {
  return unwrap(
    await api.GET('/tasks/{taskId}/comments', {
      params: { path: { taskId }, query: { page, pageSize: COMMENT_PAGE_SIZE } },
    }),
  )
}

/** The author and date are set by the server. */
export async function addComment(taskId: string, body: string): Promise<Comment> {
  return unwrap(
    await api.POST('/tasks/{taskId}/comments', {
      params: { ...XSRF, path: { taskId } },
      body: { body: body.trim() },
    }),
  )
}
