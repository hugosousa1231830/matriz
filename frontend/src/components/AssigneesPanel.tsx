import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { listMembers } from '../api/projects'
import { assignTask, errorMessage, invalidateTaskViews, unassignTask, type Task } from '../api/tasks'

interface Props {
  task: Task
  /** Managers and Administrators on an active project. */
  canManage: boolean
}

/** Assignees of a task; Managers add eligible members (the project's members) and remove each assignee. */
export function AssigneesPanel({ task, canManage }: Props) {
  const queryClient = useQueryClient()
  const [selected, setSelected] = useState('')
  const members = useQuery({
    queryKey: ['project', task.projectId, 'members'],
    queryFn: () => listMembers(task.projectId),
    enabled: canManage,
  })

  async function refresh(updated: Task) {
    queryClient.setQueryData(['task', task.id], updated)
    await invalidateTaskViews(queryClient, task.projectId)
  }

  const assign = useMutation({
    mutationFn: (userId: string) => assignTask(task.id, userId),
    onSuccess: async (updated) => {
      setSelected('')
      await refresh(updated)
    },
  })
  const unassign = useMutation({
    mutationFn: (userId: string) => unassignTask(task.id, userId),
    onSuccess: (_, userId) => refresh({ ...task, assignees: task.assignees.filter((a) => a.id !== userId) }),
  })
  const pending = assign.isPending || unassign.isPending
  const error = assign.error ?? unassign.error
  const eligible = (members.data ?? []).filter((m) => !task.assignees.some((a) => a.id === m.user.id))

  return (
    <section aria-labelledby="assignees-title" className="panel">
      <h2 id="assignees-title">Assignees</h2>
      {error && (
        <p role="alert" className="error">
          {errorMessage(error)}
        </p>
      )}
      {task.assignees.length === 0 ? (
        <p className="muted">Unassigned.</p>
      ) : (
        <ul className="member-list">
          {task.assignees.map((assignee) => (
            <li key={assignee.id}>
              <span>{assignee.name}</span>
              {canManage && (
                <button type="button" onClick={() => unassign.mutate(assignee.id)} disabled={pending}>
                  Remove {assignee.name}
                </button>
              )}
            </li>
          ))}
        </ul>
      )}
      {canManage && (
        <form
          className="toolbar"
          onSubmit={(event) => {
            event.preventDefault()
            if (selected) assign.mutate(selected)
          }}
        >
          <label htmlFor="assignee-select">Add assignee</label>
          <select
            id="assignee-select"
            value={selected}
            onChange={(event) => setSelected(event.target.value)}
            disabled={pending || eligible.length === 0}
          >
            <option value="">{eligible.length === 0 ? 'No other members' : 'Choose a member'}</option>
            {eligible.map((member) => (
              <option key={member.user.id} value={member.user.id}>
                {member.user.name}
              </option>
            ))}
          </select>
          <button type="submit" disabled={pending || !selected}>
            {assign.isPending ? 'Assigning…' : 'Assign'}
          </button>
        </form>
      )}
    </section>
  )
}
