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
      <span className="error-icon" aria-hidden="true">
        !
      </span>
      <div className="error-content">
        <p>{children}</p>
        {(action || onRetry) && (
          <div className="actions">
            {action}
            {onRetry && (
              <button type="button" onClick={onRetry} disabled={retrying}>
                {retrying ? 'Retrying…' : 'Try again'}
              </button>
            )}
          </div>
        )}
      </div>
    </div>
  )
}

/** A "nothing here yet" message with an optional next step and a decorative emoji. */
export function EmptyState({ children, action, emoji = '🗂️' }: { children: ReactNode; action?: ReactNode; emoji?: string }) {
  return (
    <div className="empty-state">
      {emoji && (
        <span className="empty-state-emoji" aria-hidden="true">
          {emoji}
        </span>
      )}
      <p>{children}</p>
      {action}
    </div>
  )
}

/** A loading message announced politely to screen readers, optionally with skeleton placeholder lines. */
export function Loading({ children, skeleton = false, lines = 3 }: { children: ReactNode; skeleton?: boolean; lines?: number }) {
  if (!skeleton) {
    return (
      <p className="muted" aria-live="polite">
        {children}
      </p>
    )
  }
  return (
    <div className="loading-skeleton">
      <p className="muted" aria-live="polite">
        {children}
      </p>
      <div aria-hidden="true">
        {Array.from({ length: lines }, (_, index) => (
          <span key={index} className="skeleton skeleton-line" />
        ))}
      </div>
    </div>
  )
}
