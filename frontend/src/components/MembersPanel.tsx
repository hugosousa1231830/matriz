import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  addMember,
  CANDIDATE_SEARCH_MAX,
  CANDIDATE_SEARCH_MIN,
  changeMemberRole,
  errorMessage,
  invalidateProjectViews,
  listMembers,
  removeMember,
  searchMemberCandidates,
  type ProjectMember,
  type ProjectRole,
  ROLE_LABELS,
} from '../api/projects'
import { useFlash } from '../hooks'
import { Avatar } from './Avatar'
import { ConfirmDialog } from './ConfirmDialog'
import { ErrorMessage, Loading } from './Feedback'

interface Props {
  projectId: string
  /** Managers and Administrators on an active project. */
  canManage: boolean
}

/** Project members; Managers add members (user search), change roles and remove members (with confirmation). */
export function MembersPanel({ projectId, canManage }: Props) {
  const queryClient = useQueryClient()
  const flash = useFlash()
  const [confirmRemove, setConfirmRemove] = useState<ProjectMember>()
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [searchError, setSearchError] = useState<string>()
  const [newRole, setNewRole] = useState<ProjectRole>('MEMBER')
  const members = useQuery({ queryKey: ['project', projectId, 'members'], queryFn: () => listMembers(projectId) })

  function refresh() {
    return invalidateProjectViews(queryClient, projectId)
  }

  const role = useMutation({
    mutationFn: ({ userId, value }: { userId: string; value: ProjectRole }) =>
      changeMemberRole(projectId, userId, value),
    onSuccess: refresh,
  })
  const remove = useMutation({
    mutationFn: (member: ProjectMember) => removeMember(projectId, member.user.id),
    onSuccess: async (_, member) => {
      setConfirmRemove(undefined)
      await refresh()
      flash(`${member.user.name} removed from the project.`)
    },
  })
  const candidates = useQuery({
    queryKey: ['project', projectId, 'member-candidates', search],
    queryFn: () => searchMemberCandidates(projectId, search),
    enabled: canManage && search !== '',
  })
  const add = useMutation({
    mutationFn: (userId: string) => addMember(projectId, userId, newRole),
    onSuccess: async (added) => {
      await refresh()
      flash(`${added.user.name} added as ${ROLE_LABELS[added.role]}.`)
    },
  })
  const error = role.error ?? add.error

  function handleSearch(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const trimmed = searchInput.trim()
    if (trimmed.length < CANDIDATE_SEARCH_MIN || trimmed.length > CANDIDATE_SEARCH_MAX) {
      setSearchError(`Search must have between ${CANDIDATE_SEARCH_MIN} and ${CANDIDATE_SEARCH_MAX} characters.`)
      return
    }
    setSearchError(undefined)
    setSearch(trimmed)
  }

  return (
    <section id="members" aria-labelledby="members-title" className="panel">
      <h2 id="members-title">Members</h2>
      {members.isPending && <Loading>Loading members…</Loading>}
      {members.isError && (
        <ErrorMessage onRetry={() => members.refetch()} retrying={members.isFetching}>
          {errorMessage(members.error)}
        </ErrorMessage>
      )}
      {error && <ErrorMessage>{errorMessage(error)}</ErrorMessage>}
      {members.data && (
        <ul className="member-list">
          {members.data.map((member) => (
            <li key={member.user.id}>
              <span className="person">
                <Avatar name={member.user.name} />
                <span className="person-text">
                  <span>
                    {member.user.name}
                    {member.isOwner && <span className="badge pill">Owner</span>}
                    {!member.active && <span className="badge pill archived">Deactivated</span>}
                  </span>
                  <span className="muted">{member.user.email}</span>
                </span>
              </span>
              {canManage && !member.isOwner ? (
                <span className="actions">
                  <label className="visually-hidden" htmlFor={`role-${member.user.id}`}>
                    Role of {member.user.name}
                  </label>
                  <select
                    id={`role-${member.user.id}`}
                    value={member.role}
                    disabled={role.isPending}
                    onChange={(event) =>
                      role.mutate({ userId: member.user.id, value: event.target.value as ProjectRole })
                    }
                  >
                    <option value="MEMBER">Member</option>
                    <option value="MANAGER">Manager</option>
                  </select>
                  <button type="button" className="secondary small" onClick={() => setConfirmRemove(member)}>
                    Remove {member.user.name}
                  </button>
                </span>
              ) : (
                <span className="badge pill role">{ROLE_LABELS[member.role]}</span>
              )}
            </li>
          ))}
        </ul>
      )}
      {confirmRemove && (
        <ConfirmDialog
          title="Remove member?"
          confirmLabel="Confirm removal"
          pendingLabel="Removing…"
          pending={remove.isPending}
          error={remove.isError && <ErrorMessage>{errorMessage(remove.error)}</ErrorMessage>}
          onConfirm={() => remove.mutate(confirmRemove)}
          onCancel={() => {
            setConfirmRemove(undefined)
            remove.reset()
          }}
        >
          <p>
            Remove {confirmRemove.user.name} from the project? Their task assignments in this project are removed too.
          </p>
        </ConfirmDialog>
      )}
      {canManage && (
        <form className="candidate-search" onSubmit={handleSearch} noValidate>
          <h3>Add a member</h3>
          <label htmlFor="candidate-search">Search users by name or email</label>
          <div className="toolbar">
            <input
              id="candidate-search"
              type="search"
              value={searchInput}
              onChange={(event) => setSearchInput(event.target.value)}
              aria-invalid={Boolean(searchError)}
              aria-describedby={searchError ? 'candidate-search-error' : undefined}
            />
            <label className="visually-hidden" htmlFor="candidate-role">
              Role of the new member
            </label>
            <select id="candidate-role" value={newRole} onChange={(event) => setNewRole(event.target.value as ProjectRole)}>
              <option value="MEMBER">Member</option>
              <option value="MANAGER">Manager</option>
            </select>
            <button type="submit">Search</button>
          </div>
          {searchError && (
            <p id="candidate-search-error" className="field-error">
              {searchError}
            </p>
          )}
          {candidates.isFetching && <Loading>Searching…</Loading>}
          {candidates.isError && <ErrorMessage>{errorMessage(candidates.error)}</ErrorMessage>}
          {candidates.data && !candidates.isFetching && candidates.data.length === 0 && (
            <p className="muted">No users found who are not members yet.</p>
          )}
          {candidates.data && candidates.data.length > 0 && (
            <ul className="member-list" aria-label="Users to add">
              {candidates.data.map((candidate) => (
                <li key={candidate.id}>
                  <span className="person">
                    <Avatar name={candidate.name} />
                    <span className="person-text">
                      <span>{candidate.name}</span>
                      <span className="muted">{candidate.email}</span>
                    </span>
                  </span>
                  <button type="button" className="secondary small" onClick={() => add.mutate(candidate.id)} disabled={add.isPending}>
                    Add {candidate.name}
                  </button>
                </li>
              ))}
            </ul>
          )}
        </form>
      )}
    </section>
  )
}
