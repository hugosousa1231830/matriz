import { useState, type FormEvent, type ReactNode } from 'react'
import {
  DESCRIPTION_MAX,
  errorMessage,
  PRIORITIES,
  PRIORITY_LABELS,
  serverFieldErrors,
  STATUSES,
  STATUS_LABELS,
  TITLE_MAX,
  TITLE_MIN,
  validateTask,
  type TaskFields,
  type TaskPriority,
  type TaskStatus,
} from '../api/tasks'
import { ErrorMessage } from './Feedback'
import { FormField } from './FormField'

export interface TaskValues {
  title: string
  description: string
  status: TaskStatus
  priority: TaskPriority
  assigneeIds: string[]
}

export interface AssigneeOption {
  id: string
  name: string
}

interface Props {
  idPrefix: string
  submitLabel: string
  pendingLabel: string
  initial?: Partial<TaskValues>
  /** Shows the status field (editing); new tasks always start as To do. */
  withStatus?: boolean
  /** Shows the assignee picker (creating); the options are the project's active members. */
  assigneeOptions?: AssigneeOption[]
  /** State of the assignee options while they are not available. */
  assigneeOptionsState?: 'loading' | 'error'
  /** Rejects with the API error; the input is kept so the user can correct it or retry. */
  onSubmit: (values: TaskValues) => Promise<unknown>
  onCancel?: () => void
  /** Extra content under the form error, e.g. a reload button after a version conflict. */
  errorAction?: (error: unknown) => ReactNode
}

/**
 * Title, description, priority (and status or assignees) form shared by create
 * and edit (client and server validation).
 */
export function TaskForm({
  idPrefix,
  submitLabel,
  pendingLabel,
  initial = {},
  withStatus = false,
  assigneeOptions,
  assigneeOptionsState,
  onSubmit,
  onCancel,
  errorAction,
}: Props) {
  const [title, setTitle] = useState(initial.title ?? '')
  const [description, setDescription] = useState(initial.description ?? '')
  const [status, setStatus] = useState<TaskStatus>(initial.status ?? 'TODO')
  const [priority, setPriority] = useState<TaskPriority>(initial.priority ?? 'MEDIUM')
  const [assigneeIds, setAssigneeIds] = useState<string[]>(initial.assigneeIds ?? [])
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
      await onSubmit({ title, description, status, priority, assigneeIds })
      if (initial.title === undefined) {
        setTitle('')
        setDescription('')
        setPriority('MEDIUM')
        setAssigneeIds([])
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
        <ErrorMessage action={errorAction?.(formError)}>{errorMessage(formError)}</ErrorMessage>
      )}
      <FormField id={`${idPrefix}-title`} label="Title" error={errors.title} hint={`${TITLE_MIN}–${TITLE_MAX} characters.`}>
        {(control) => (
          <input {...control} value={title} onChange={(event) => setTitle(event.target.value)} disabled={pending} />
        )}
      </FormField>
      <FormField
        id={`${idPrefix}-description`}
        label="Description"
        error={errors.description}
        hint={`Optional, up to ${DESCRIPTION_MAX} characters.`}
      >
        {(control) => (
          <textarea
            {...control}
            value={description}
            rows={4}
            onChange={(event) => setDescription(event.target.value)}
            disabled={pending}
          />
        )}
      </FormField>
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
      {assigneeOptions && (
        <fieldset className="assignee-picker">
          <legend>Assignees</legend>
          {assigneeOptionsState === 'loading' && <p className="muted">Loading members…</p>}
          {assigneeOptionsState === 'error' && (
            <p className="muted">Members could not be loaded. You can assign people from the task page later.</p>
          )}
          {!assigneeOptionsState && assigneeOptions.length === 0 && (
            <p className="muted">No active members to assign.</p>
          )}
          {assigneeOptions.map((option) => (
            <label key={option.id} className="checkbox">
              <input
                type="checkbox"
                checked={assigneeIds.includes(option.id)}
                onChange={() =>
                  setAssigneeIds((current) =>
                    current.includes(option.id) ? current.filter((id) => id !== option.id) : [...current, option.id],
                  )
                }
                disabled={pending}
              />
              {option.name}
            </label>
          ))}
        </fieldset>
      )}
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
