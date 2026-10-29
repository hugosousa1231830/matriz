import type { ReactNode } from 'react'

interface Props {
  children: ReactNode
  /** Shows a "Try again" button (e.g. refetch after a network error). */
  onRetry?: () => void
  retrying?: boolean
  /** Other next step, e.g. "Reload task" after a version conflict. */
  action?: ReactNode
}

/** An error announced to screen readers, optionally with a retry action. */
export function ErrorMessage({ children, onRetry, retrying = false, action }: Props) {
  return (
    <div role="alert" className="error">
      <p>{children}</p>
      {action}
      {onRetry && (
        <button type="button" onClick={onRetry} disabled={retrying}>
          {retrying ? 'Retrying…' : 'Try again'}
        </button>
      )}
    </div>
  )
}

/** A "nothing here yet" message with an optional next step. */
export function EmptyState({ children, action }: { children: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty-state">
      <p>{children}</p>
      {action}
    </div>
  )
}

/** A loading message announced politely to screen readers. */
export function Loading({ children }: { children: ReactNode }) {
  return (
    <p className="muted" aria-live="polite">
      {children}
    </p>
  )
}
