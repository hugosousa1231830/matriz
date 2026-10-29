import { PRIORITY_LABELS, STATUS_LABELS, type TaskPriority, type TaskStatus } from '../api/tasks'

export function StatusBadge({ status }: { status: TaskStatus }) {
  return <span className={`badge status-${status.toLowerCase().replace('_', '-')}`}>{STATUS_LABELS[status]}</span>
}

export function PriorityBadge({ priority }: { priority: TaskPriority }) {
  return <span className={`badge priority-${priority.toLowerCase()}`}>{PRIORITY_LABELS[priority]}</span>
}

export function ProjectStatusBadge({ archived }: { archived: boolean }) {
  return archived ? <span className="badge archived">Archived</span> : <span className="badge active">Active</span>
}
