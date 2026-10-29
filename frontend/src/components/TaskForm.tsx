import { useState, type FormEvent, type ReactNode } from 'react'
import {
  errorMessage,
  PRIORITIES,
  PRIORITY_LABELS,
  serverFieldErrors,
  STATUSES,
  STATUS_LABELS,
  validateTask,
  type TaskFields,
  type TaskPriority,
  type TaskStatus,
} from '../api/tasks'

export interface TaskValues {
  title: string
  description: string
  status: TaskStatus
  priority: TaskPriority
}

interface Props {
  idPrefix: string
  submitLabel: string
  pendingLabel: string
  initial?: Partial<TaskValues>
  /** Shows the status field (editing); new tasks always start as To do. */
  withStatus?: boolean
  /** Rejects with the API error; the input is kept so the user can correct it or retry. */
  onSubmit: (values: TaskValues) => Promise<unknown>
  onCancel?: () => void
  /** Extra content under the form error, e.g. a reload button after a version conflict. */
  errorAction?: (error: unknown) => ReactNode
}

/** Title, description, priority (and status) form shared by create and edit (client and server validation). */
export function TaskForm({
  idPrefix,
  submitLabel,
  pendingLabel,
  initial = {},
  withStatus = false,
  onSubmit,
  onCancel,
  errorAction,
}: Props) {
  const [title, setTitle] = useState(initial.title ?? '')
  const [description, setDescription] = useState(initial.description ?? '')
  const [status, setStatus] = useState<TaskStatus>(initial.status ?? 'TODO')
  const [priority, setPriority] = useState<TaskPriority>(initial.priority ?? 'MEDIUM')
  const [errors, setErrors] = useState<TaskFields>({})
  const [formError, setFormError] = useState<unknown>()
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateTask(title, description)
    setErrors(found)
    setFormError(undefined)
    if (found.title || found.description) {
      return
    }
    setPending(true)
    try {
      await onSubmit({ title, description, status, priority })
      if (initial.title === undefined) {
        setTitle('')
        setDescription('')
        setPriority('MEDIUM')
      }
    } catch (error) {
      const fields = serverFieldErrors(error)
      setErrors(fields)
      if (!fields.title && !fields.description) {
        setFormError(error)
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="task-form" onSubmit={handleSubmit} noValidate>
      {formError !== undefined && (
        <div role="alert" className="error">
          <p>{errorMessage(formError)}</p>
          {errorAction?.(formError)}
        </div>
      )}
      <label htmlFor={`${idPrefix}-title`}>Title</label>
      <input
        id={`${idPrefix}-title`}
        value={title}
        onChange={(event) => setTitle(event.target.value)}
        aria-invalid={Boolean(errors.title)}
        aria-describedby={errors.title ? `${idPrefix}-title-error` : undefined}
        disabled={pending}
      />
      {errors.title && (
        <p id={`${idPrefix}-title-error`} className="field-error">
          {errors.title}
        </p>
      )}
      <label htmlFor={`${idPrefix}-description`}>Description</label>
      <textarea
        id={`${idPrefix}-description`}
        value={description}
        rows={4}
        onChange={(event) => setDescription(event.target.value)}
        aria-invalid={Boolean(errors.description)}
        aria-describedby={errors.description ? `${idPrefix}-description-error` : undefined}
        disabled={pending}
      />
      {errors.description && (
        <p id={`${idPrefix}-description-error`} className="field-error">
          {errors.description}
        </p>
      )}
      <div className="field-row">
        {withStatus && (
          <span>
            <label htmlFor={`${idPrefix}-status`}>Status</label>
            <select
              id={`${idPrefix}-status`}
              value={status}
              onChange={(event) => setStatus(event.target.value as TaskStatus)}
              disabled={pending}
            >
              {STATUSES.map((value) => (
                <option key={value} value={value}>
                  {STATUS_LABELS[value]}
                </option>
              ))}
            </select>
          </span>
        )}
        <span>
          <label htmlFor={`${idPrefix}-priority`}>Priority</label>
          <select
            id={`${idPrefix}-priority`}
            value={priority}
            onChange={(event) => setPriority(event.target.value as TaskPriority)}
            disabled={pending}
          >
            {PRIORITIES.map((value) => (
              <option key={value} value={value}>
                {PRIORITY_LABELS[value]}
              </option>
            ))}
          </select>
        </span>
      </div>
      <div className="actions">
        <button type="submit" disabled={pending}>
          {pending ? pendingLabel : submitLabel}
        </button>
        {onCancel && (
          <button type="button" onClick={onCancel} disabled={pending}>
            Cancel
          </button>
        )}
      </div>
    </form>
  )
}
