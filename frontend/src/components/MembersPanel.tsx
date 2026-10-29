import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { changeMemberRole, errorMessage, listMembers, removeMember, type ProjectRole } from '../api/projects'

interface Props {
  projectId: string
  /** Managers and Administrators on an active project. */
  canManage: boolean
}

/** Project members; Managers change roles and remove members (with confirmation). */
export function MembersPanel({ projectId, canManage }: Props) {
  const queryClient = useQueryClient()
  const [confirmRemove, setConfirmRemove] = useState<string>()
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
  const error = role.error ?? remove.error

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
    </section>
  )
}
