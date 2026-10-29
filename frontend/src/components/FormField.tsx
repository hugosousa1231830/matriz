import type { ReactNode } from 'react'

/** Accessibility attributes linking a control to its label and error message. */
export interface FieldControlProps {
  id: string
  'aria-invalid': boolean
  'aria-describedby': string | undefined
}

interface Props {
  id: string
  label: string
  error?: string
  /** Visible helper text under the control (e.g. a length limit). */
  hint?: string
  /** Renders the control with the given id and ARIA attributes. */
  children: (control: FieldControlProps) => ReactNode
}

/** A labelled form control with its hint and validation message, wired for screen readers. */
export function FormField({ id, label, error, hint, children }: Props) {
  const describedBy = [error && `${id}-error`, hint && `${id}-hint`].filter(Boolean).join(' ') || undefined
  return (
    <div className="form-field">
      <label htmlFor={id}>{label}</label>
      {children({ id, 'aria-invalid': Boolean(error), 'aria-describedby': describedBy })}
      {hint && (
        <p id={`${id}-hint`} className="muted hint">
          {hint}
        </p>
      )}
      {error && (
        <p id={`${id}-error`} className="field-error">
          {error}
        </p>
      )}
    </div>
  )
}
