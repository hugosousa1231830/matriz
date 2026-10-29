import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import {
  addMember,
  CANDIDATE_SEARCH_MAX,
  CANDIDATE_SEARCH_MIN,
  changeMemberRole,
  errorMessage,
  listMembers,
  removeMember,
  searchMemberCandidates,
  type ProjectRole,
} from '../api/projects'

interface Props {
  projectId: string
  /** Managers and Administrators on an active project. */
  canManage: boolean
}

/** Project members; Managers add members (user search), change roles and remove members (with confirmation). */
export function MembersPanel({ projectId, canManage }: Props) {
  const queryClient = useQueryClient()
  const [confirmRemove, setConfirmRemove] = useState<string>()
  const [searchInput, setSearchInput] = useState('')
  const [search, setSearch] = useState('')
  const [searchError, setSearchError] = useState<string>()
  const [newRole, setNewRole] = useState<ProjectRole>('MEMBER')
  const members = useQuery({ queryKey: ['project', projectId, 'members'], queryFn: () => listMembers(projectId) })

  async function refresh() {
    await queryClient.invalidateQueries({ queryKey: ['project', projectId] })
    await queryClient.invalidateQueries({ queryKey: ['projects'] })
  }

  const role = useMutation({
    mutationFn: ({ userId, value }: { userId: string; value: ProjectRole }) =>
      changeMemberRole(projectId, userId, value),
    onSuccess: refresh,
  })
  const remove = useMutation({
    mutationFn: (userId: string) => removeMember(projectId, userId),
    onSuccess: async () => {
      setConfirmRemove(undefined)
      await refresh()
    },
  })
  const candidates = useQuery({
    queryKey: ['project', projectId, 'member-candidates', search],
    queryFn: () => searchMemberCandidates(projectId, search),
    enabled: canManage && search !== '',
  })
  const add = useMutation({
    mutationFn: (userId: string) => addMember(projectId, userId, newRole),
    onSuccess: refresh,
  })
  const error = role.error ?? remove.error ?? add.error

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
    <section aria-labelledby="members-title" className="panel">
      <h2 id="members-title">Members</h2>
      {members.isPending && <p>Loading members…</p>}
      {members.isError && (
        <p role="alert" className="error">
          {errorMessage(members.error)}
        </p>
      )}
      {error && (
        <p role="alert" className="error">
          {errorMessage(error)}
        </p>
      )}
      {members.data && (
        <ul className="member-list">
          {members.data.map((member) => (
            <li key={member.user.id}>
              <span>
                {member.user.name} <span className="muted">{member.user.email}</span>
                {member.isOwner && <span className="badge">Owner</span>}
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
                  {confirmRemove === member.user.id ? (
                    <>
                      <button type="button" onClick={() => remove.mutate(member.user.id)} disabled={remove.isPending}>
                        Confirm removal
                      </button>
                      <button type="button" onClick={() => setConfirmRemove(undefined)}>
                        Cancel
                      </button>
                    </>
                  ) : (
                    <button type="button" onClick={() => setConfirmRemove(member.user.id)}>
                      Remove {member.user.name}
                    </button>
                  )}
                </span>
              ) : (
                <span className="muted">{member.role === 'MANAGER' ? 'Manager' : 'Member'}</span>
              )}
            </li>
          ))}
        </ul>
      )}
      {confirmRemove && (
        <p className="notice">Removing a member also removes their task assignments in this project.</p>
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
          {candidates.isFetching && <p>Searching…</p>}
          {candidates.isError && (
            <p role="alert" className="error">
              {errorMessage(candidates.error)}
            </p>
          )}
          {candidates.data && !candidates.isFetching && candidates.data.length === 0 && (
            <p className="muted">No users found who are not members yet.</p>
          )}
          {candidates.data && candidates.data.length > 0 && (
            <ul className="member-list" aria-label="Users to add">
              {candidates.data.map((candidate) => (
                <li key={candidate.id}>
                  <span>
                    {candidate.name} <span className="muted">{candidate.email}</span>
                  </span>
                  <button type="button" onClick={() => add.mutate(candidate.id)} disabled={add.isPending}>
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
