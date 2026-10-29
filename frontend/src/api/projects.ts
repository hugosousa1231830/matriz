import { api } from './client'
import { ApiError } from './auth'
import type { components } from './schema'

export type Project = components['schemas']['Project']
export type ProjectPage = components['schemas']['ProjectPage']
export type ProjectMember = components['schemas']['ProjectMember']
export type ProjectRole = components['schemas']['ProjectRole']
export type UserContact = components['schemas']['UserContact']

/** Placeholder; the session middleware sets the real header from the XSRF-TOKEN cookie. */
export const XSRF = { header: { 'X-XSRF-TOKEN': '' } }

export const PAGE_SIZE = 20
export const NAME_MIN = 3
export const NAME_MAX = 100
export const DESCRIPTION_MAX = 1000
export const CANDIDATE_SEARCH_MIN = 2
export const CANDIDATE_SEARCH_MAX = 100

export interface ProjectFields {
  name?: string
  description?: string
}

/** Same rules as the API (trimmed name 3–100, description ≤ 1000). */
export function validateProject(name: string, description: string): ProjectFields {
  const errors: ProjectFields = {}
  const trimmed = name.trim()
  if (!trimmed) {
    errors.name = 'Name is required.'
  } else if (trimmed.length < NAME_MIN || trimmed.length > NAME_MAX) {
    errors.name = `Name must have between ${NAME_MIN} and ${NAME_MAX} characters.`
  }
  if (description.trim().length > DESCRIPTION_MAX) {
    errors.description = `Description must have at most ${DESCRIPTION_MAX} characters.`
  }
  return errors
}

/** Field errors of a `400` response, for the project form. */
export function serverFieldErrors(error: unknown): ProjectFields {
  const errors: ProjectFields = {}
  if (error instanceof ApiError) {
    for (const { field, message } of error.fieldErrors) {
      if (field === 'name' || field === 'description') {
        errors[field] = message
      }
    }
  }
  return errors
}

/** User-facing message for a failed project or member operation. */
export function errorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    switch (error.code) {
      case 'PROJECT_ARCHIVED':
        return 'This project is archived and read-only.'
      case 'FORBIDDEN':
        return 'You are not allowed to do this.'
      case 'NOT_FOUND':
        return 'Project not found.'
      case 'OWNER_IMMUTABLE':
        return 'The project owner cannot be removed or demoted.'
      case 'ALREADY_MEMBER':
        return 'This person is already a member of the project.'
      case 'USER_NOT_ELIGIBLE':
        return 'Only active users can be added as members.'
      case 'VALIDATION_ERROR':
        return 'Please correct the highlighted fields.'
    }
  }
  return 'Something went wrong. Please try again.'
}

/** The response body, or an {@link ApiError} for a problem response. */
export function unwrap<T>(result: { data?: T; error?: unknown; response: Response }): T {
  if (result.data === undefined) {
    throw new ApiError(result.response.status, result.error as ConstructorParameters<typeof ApiError>[1])
  }
  return result.data
}

export async function listProjects(query: {
  search?: string
  includeArchived?: boolean
  page?: number
}): Promise<ProjectPage> {
  return unwrap(
    await api.GET('/projects', {
      params: {
        query: {
          search: query.search || undefined,
          includeArchived: query.includeArchived || undefined,
          page: query.page,
          pageSize: PAGE_SIZE,
        },
      },
    }),
  )
}

export async function getProject(projectId: string): Promise<Project> {
  return unwrap(await api.GET('/projects/{projectId}', { params: { path: { projectId } } }))
}

export async function createProject(name: string, description: string): Promise<Project> {
  return unwrap(
    await api.POST('/projects', {
      params: XSRF,
      body: { name: name.trim(), description: description.trim() || null },
    }),
  )
}

export async function updateProject(projectId: string, name: string, description: string): Promise<Project> {
  return unwrap(
    await api.PATCH('/projects/{projectId}', {
      params: { ...XSRF, path: { projectId } },
      body: { name: name.trim(), description: description.trim() || null },
    }),
  )
}

export async function setArchived(projectId: string, archived: boolean): Promise<Project> {
  const params = { ...XSRF, path: { projectId } }
  return unwrap(
    archived
      ? await api.POST('/projects/{projectId}/archive', { params })
      : await api.POST('/projects/{projectId}/unarchive', { params }),
  )
}

export async function listMembers(projectId: string): Promise<ProjectMember[]> {
  return unwrap(await api.GET('/projects/{projectId}/members', { params: { path: { projectId } } }))
}

/** Active users who are not members yet, matched on name or email (at most 20). */
export async function searchMemberCandidates(projectId: string, search: string): Promise<UserContact[]> {
  return unwrap(
    await api.GET('/projects/{projectId}/member-candidates', {
      params: { path: { projectId }, query: { search: search.trim() } },
    }),
  )
}

export async function addMember(projectId: string, userId: string, role: ProjectRole): Promise<ProjectMember> {
  return unwrap(
    await api.POST('/projects/{projectId}/members', {
      params: { ...XSRF, path: { projectId } },
      body: { userId, role },
    }),
  )
}

export async function changeMemberRole(projectId: string, userId: string, role: ProjectRole): Promise<ProjectMember> {
  return unwrap(
    await api.PATCH('/projects/{projectId}/members/{userId}', {
      params: { ...XSRF, path: { projectId, userId } },
      body: { role },
    }),
  )
}

export async function removeMember(projectId: string, userId: string): Promise<void> {
  const { error, response } = await api.DELETE('/projects/{projectId}/members/{userId}', {
    params: { ...XSRF, path: { projectId, userId } },
  })
  if (!response.ok) {
    throw new ApiError(response.status, error)
  }
}
