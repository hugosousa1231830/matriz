import { useState, type FormEvent } from 'react'
import { errorMessage, serverFieldErrors, validateProject, type ProjectFields } from '../api/projects'

interface Props {
  idPrefix: string
  submitLabel: string
  pendingLabel: string
  initialName?: string
  initialDescription?: string
  /** Rejects with the API error; the input is kept so the user can correct it. */
  onSubmit: (name: string, description: string) => Promise<unknown>
  onCancel?: () => void
}

/** Name and description form shared by create and edit (client and server validation). */
export function ProjectForm({
  idPrefix,
  submitLabel,
  pendingLabel,
  initialName = '',
  initialDescription = '',
  onSubmit,
  onCancel,
}: Props) {
  const [name, setName] = useState(initialName)
  const [description, setDescription] = useState(initialDescription)
  const [errors, setErrors] = useState<ProjectFields>({})
  const [formError, setFormError] = useState<string>()
  const [pending, setPending] = useState(false)

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateProject(name, description)
    setErrors(found)
    setFormError(undefined)
    if (found.name || found.description) {
      return
    }
    setPending(true)
    try {
      await onSubmit(name, description)
      if (!initialName) {
        setName('')
        setDescription('')
      }
    } catch (error) {
      const fields = serverFieldErrors(error)
      setErrors(fields)
      if (!fields.name && !fields.description) {
        setFormError(errorMessage(error))
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <form className="project-form" onSubmit={handleSubmit} noValidate>
      {formError && (
        <p role="alert" className="error">
          {formError}
        </p>
      )}
      <label htmlFor={`${idPrefix}-name`}>Name</label>
      <input
        id={`${idPrefix}-name`}
        value={name}
        onChange={(event) => setName(event.target.value)}
        aria-invalid={Boolean(errors.name)}
        aria-describedby={errors.name ? `${idPrefix}-name-error` : undefined}
        disabled={pending}
      />
      {errors.name && (
        <p id={`${idPrefix}-name-error`} className="field-error">
          {errors.name}
        </p>
      )}
      <label htmlFor={`${idPrefix}-description`}>Description</label>
      <textarea
        id={`${idPrefix}-description`}
        value={description}
        rows={3}
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
