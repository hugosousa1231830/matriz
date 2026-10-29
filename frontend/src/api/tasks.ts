import type { QueryClient } from '@tanstack/react-query'
import { api } from './client'
import { ApiError } from './auth'
import { unwrap, XSRF } from './projects'
import type { components, operations } from './schema'

export type Task = components['schemas']['Task']
export type TaskPage = components['schemas']['TaskPage']
export type TaskStatus = components['schemas']['TaskStatus']
export type TaskPriority = components['schemas']['TaskPriority']
export type TaskSort = NonNullable<NonNullable<operations['listProjectTasks']['parameters']['query']>['sort']>

export const TASK_PAGE_SIZE = 20
export const TITLE_MIN = 3
export const TITLE_MAX = 150
export const DESCRIPTION_MAX = 2000

export const STATUSES: TaskStatus[] = ['TODO', 'IN_PROGRESS', 'DONE']
export const PRIORITIES: TaskPriority[] = ['LOW', 'MEDIUM', 'HIGH']

export const STATUS_LABELS: Record<TaskStatus, string> = {
  TODO: 'To do',
  IN_PROGRESS: 'In progress',
  DONE: 'Done',
}

export const PRIORITY_LABELS: Record<TaskPriority, string> = {
  LOW: 'Low',
  MEDIUM: 'Medium',
  HIGH: 'High',
}

export const SORT_LABELS: Record<TaskSort, string> = {
  '-createdAt': 'Newest first',
  createdAt: 'Oldest first',
  '-priority': 'Priority (high to low)',
  priority: 'Priority (low to high)',
  status: 'Status (to do first)',
  '-status': 'Status (done first)',
  title: 'Title (A–Z)',
  '-title': 'Title (Z–A)',
}

export interface TaskFields {
  title?: string
  description?: string
}

/** Same rules as the API (trimmed title 3–150, not whitespace-only; description ≤ 2000). */
export function validateTask(title: string, description: string): TaskFields {
  const errors: TaskFields = {}
  const trimmed = title.trim()
  if (!trimmed) {
    errors.title = 'Title is required.'
  } else if (trimmed.length < TITLE_MIN || trimmed.length > TITLE_MAX) {
    errors.title = `Title must have between ${TITLE_MIN} and ${TITLE_MAX} characters.`
  }
  if (description.trim().length > DESCRIPTION_MAX) {
    errors.description = `Description must have at most ${DESCRIPTION_MAX} characters.`
  }
  return errors
}

/** Field errors of a `400` response, for the task form. */
export function serverFieldErrors(error: unknown): TaskFields {
  const errors: TaskFields = {}
  if (error instanceof ApiError) {
    for (const { field, message } of error.fieldErrors) {
      if (field === 'title' || field === 'description') {
        errors[field] = message
      }
    }
  }
  return errors
}

/** User-facing message for a failed task operation. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'VERSION_CONFLICT':
        return 'This task was changed by someone else. Reload it to see the latest version; your changes are kept.'
      case 'PROJECT_ARCHIVED':
        return 'This project is archived and read-only.'
      case 'FORBIDDEN':
        return 'You are not allowed to do this.'
      case 'NOT_FOUND':
        return 'Task not found or you do not have access to it.'
      case 'ASSIGNEE_NOT_ELIGIBLE':
        return 'Assignees must be active members of the project.'
      case 'ALREADY_ASSIGNED':
        return 'This person is already assigned to the task.'
      case 'VALIDATION_ERROR':
        return error.fieldErrors[0]?.message ?? 'Please correct the highlighted fields.'
    }
  }
  return 'Something went wrong. Please try again.'
}

export function isVersionConflict(error: unknown): boolean {
  return error instanceof ApiError && error.code === 'VERSION_CONFLICT'
}

export interface TaskListQuery {
  statuses: TaskStatus[]
  priorities: TaskPriority[]
  /** A user id, `none` for unassigned tasks, or empty for anyone. */
  assigneeId: string
  sort: TaskSort
  page: number
}

export async function listTasks(projectId: string, query: TaskListQuery): Promise<TaskPage> {
  return unwrap(
    await api.GET('/projects/{projectId}/tasks', {
      params: {
        path: { projectId },
        query: {
          status: query.statuses.length ? query.statuses : undefined,
          priority: query.priorities.length ? query.priorities : undefined,
          assigneeId: query.assigneeId || undefined,
          sort: query.sort,
          page: query.page,
          pageSize: TASK_PAGE_SIZE,
        },
      },
      // The contract uses comma-separated arrays (style form, explode false).
      querySerializer: { array: { style: 'form', explode: false } },
    }),
  )
}

export async function getTask(taskId: string): Promise<Task> {
  return unwrap(await api.GET('/tasks/{taskId}', { params: { path: { taskId } } }))
}

export async function createTask(
  projectId: string,
  fields: { title: string; description: string; priority: TaskPriority; assigneeIds?: string[] },
): Promise<Task> {
  return unwrap(
    await api.POST('/projects/{projectId}/tasks', {
      params: { ...XSRF, path: { projectId } },
      body: {
        title: fields.title.trim(),
        description: fields.description.trim() || null,
        priority: fields.priority,
        assigneeIds: fields.assigneeIds ?? [],
      },
    }),
  )
}

export interface TaskChanges {
  title?: string
  description?: string
  status?: TaskStatus
  priority?: TaskPriority
}

/** Partial update guarded by the version the client last read (`409 VERSION_CONFLICT` when stale). */
export async function updateTask(taskId: string, version: number, changes: TaskChanges): Promise<Task> {
  return unwrap(
    await api.PATCH('/tasks/{taskId}', {
      params: { ...XSRF, path: { taskId } },
      body: {
        ...changes,
        title: changes.title?.trim(),
        description: changes.description === undefined ? undefined : changes.description.trim() || null,
        version,
      },
    }),
  )
}

export async function deleteTask(taskId: string): Promise<void> {
  const { error, response } = await api.DELETE('/tasks/{taskId}', { params: { ...XSRF, path: { taskId } } })
  if (!response.ok) {
    throw new ApiError(response.status, error)
  }
}

/** Adds an eligible member as assignee; returns the updated task (its version does not change). */
export async function assignTask(taskId: string, userId: string): Promise<Task> {
  return unwrap(
    await api.POST('/tasks/{taskId}/assignees', {
      params: { ...XSRF, path: { taskId } },
      body: { userId },
    }),
  )
}

export async function unassignTask(taskId: string, userId: string): Promise<void> {
  const { error, response } = await api.DELETE('/tasks/{taskId}/assignees/{userId}', {
    params: { ...XSRF, path: { taskId, userId } },
  })
  if (!response.ok) {
    throw new ApiError(response.status, error)
  }
}

/** Refreshes everything that shows tasks or task counts (task lists, project, project list, dashboard). */
export async function invalidateTaskViews(queryClient: QueryClient, projectId: string): Promise<void> {
  await Promise.all([
    queryClient.invalidateQueries({ queryKey: ['project', projectId] }),
    queryClient.invalidateQueries({ queryKey: ['projects'] }),
    queryClient.invalidateQueries({ queryKey: ['dashboard'] }),
  ])
}
