import { useEffect, useId, useRef, type KeyboardEvent, type ReactNode } from 'react'

interface Props {
  title: string
  children: ReactNode
  confirmLabel: string
  pendingLabel: string
  pending: boolean
  /** Shown inside the dialog when the confirmed action failed; the dialog stays open. */
  error?: ReactNode
  onConfirm: () => void
  onCancel: () => void
}

const FOCUSABLE = 'button:not([disabled]), [href], input:not([disabled]), select:not([disabled]), textarea:not([disabled])'

/**
 * Modal confirmation for destructive actions. Focus moves to Cancel (the safe
 * choice), Tab stays inside the dialog, Escape cancels and focus returns to the
 * control that opened it.
 */
export function ConfirmDialog({ title, children, confirmLabel, pendingLabel, pending, error, onConfirm, onCancel }: Props) {
  const id = useId()
  const dialog = useRef<HTMLDivElement>(null)
  const cancel = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    const opener = document.activeElement as HTMLElement | null
    cancel.current?.focus()
    return () => opener?.focus()
  }, [])

  function handleKeyDown(event: KeyboardEvent<HTMLDivElement>) {
    if (event.key === 'Escape' && !pending) {
      event.stopPropagation()
      onCancel()
      return
    }
    if (event.key === 'Tab' && dialog.current) {
      const items = Array.from(dialog.current.querySelectorAll<HTMLElement>(FOCUSABLE))
      if (items.length === 0) {
        event.preventDefault()
        return
      }
      const first = items[0]
      const last = items[items.length - 1]
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first.focus()
      }
    }
  }

  return (
    <div className="dialog-backdrop">
      <div
        ref={dialog}
        role="alertdialog"
        aria-modal="true"
        aria-labelledby={`${id}-title`}
        aria-describedby={`${id}-body`}
        className="dialog"
        onKeyDown={handleKeyDown}
      >
        <h2 id={`${id}-title`}>{title}</h2>
        <div id={`${id}-body`}>{children}</div>
        {error}
        <div className="actions">
          <button type="button" className="danger" onClick={onConfirm} disabled={pending}>
            {pending ? pendingLabel : confirmLabel}
          </button>
          <button type="button" ref={cancel} onClick={onCancel} disabled={pending}>
            Cancel
          </button>
        </div>
      </div>
    </div>
  )
}
