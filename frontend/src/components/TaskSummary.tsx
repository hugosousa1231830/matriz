import type { TaskCounts } from '../api/projects'

interface Props {
  counts: TaskCounts
  /** Percentage of Done tasks, as computed by the API (rounded down). */
  percent: number
  /** Only the progress bar and its caption (for lists). */
  compact?: boolean
  /** Accessible name of the progress bar. */
  label?: string
}

/** Progress bar ("% done") and counts per status of a project or of all projects. */
export function TaskSummary({ counts, percent, compact = false, label = 'Tasks done' }: Props) {
  const caption =
    counts.total === 0
      ? '0% done — no tasks yet'
      : `${percent}% done — ${counts.todo} to do, ${counts.inProgress} in progress, ${counts.done} done (${counts.total} ${counts.total === 1 ? 'task' : 'tasks'})`
  return (
    <div className="task-summary">
      <div className="progress">
        <progress max={100} value={percent} aria-label={label} />
        <span>{caption}</span>
      </div>
      {!compact && (
        <dl className="summary-cards">
          <div>
            <dt>To do</dt>
            <dd>{counts.todo}</dd>
          </div>
          <div>
            <dt>In progress</dt>
            <dd>{counts.inProgress}</dd>
          </div>
          <div>
            <dt>Done</dt>
            <dd>{counts.done}</dd>
          </div>
        </dl>
      )}
    </div>
  )
}
