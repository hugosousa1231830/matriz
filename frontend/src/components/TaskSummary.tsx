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

const TILES = [
  { key: 'todo', label: 'To do', icon: '○' },
  { key: 'inProgress', label: 'In progress', icon: '◐' },
  { key: 'done', label: 'Done', icon: '●' },
] as const

/** Progress bar ("% done") and counts per status of a project or of all projects. */
export function TaskSummary({ counts, percent, compact = false, label = 'Tasks done' }: Props) {
  const caption =
    counts.total === 0
      ? '0% done — no tasks yet'
      : `${percent}% done — ${counts.todo} to do, ${counts.inProgress} in progress, ${counts.done} done (${counts.total} ${counts.total === 1 ? 'task' : 'tasks'})`
  const progress = (
    <div className="progress">
      <progress max={100} value={percent} aria-label={label} />
      <span>{caption}</span>
    </div>
  )
  if (compact) {
    return <div className="task-summary">{progress}</div>
  }
  return (
    <div className="task-summary">
      <dl className="summary-tiles">
        {TILES.map((tile) => (
          <div key={tile.key} className={`summary-tile tile-${tile.key}`}>
            <span className="tile-icon" aria-hidden="true">
              {tile.icon}
            </span>
            <div className="tile-text">
              <dt>{tile.label}</dt>
              <dd>{counts[tile.key]}</dd>
            </div>
          </div>
        ))}
      </dl>
      {progress}
    </div>
  )
}
