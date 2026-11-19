import { PRIORITY_LABELS, STATUS_LABELS, type TaskPriority, type TaskStatus } from '../api/tasks'

export function StatusBadge({ status }: { status: TaskStatus }) {
  return (
    <span className={`badge pill status-${status.toLowerCase().replace('_', '-')}`}>
      <span className="pill-dot" aria-hidden="true" />
      {STATUS_LABELS[status]}
    </span>
  )
}

export function PriorityBadge({ priority }: { priority: TaskPriority }) {
  return <span className={`badge pill priority-${priority.toLowerCase()}`}>{PRIORITY_LABELS[priority]}</span>
}

export function ProjectStatusBadge({ archived }: { archived: boolean }) {
  return (
    <span className={`badge pill ${archived ? 'archived' : 'active'}`}>
      <span className="pill-dot" aria-hidden="true" />
      {archived ? 'Archived' : 'Active'}
    </span>
  )
}
