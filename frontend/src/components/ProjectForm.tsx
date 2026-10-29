import { useState, type FormEvent } from 'react'
import {
  DESCRIPTION_MAX,
  errorMessage,
  NAME_MAX,
  NAME_MIN,
  serverFieldErrors,
  validateProject,
  type ProjectFields,
} from '../api/projects'
import { ErrorMessage } from './Feedback'
import { FormField } from './FormField'

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
      {formError && <ErrorMessage>{formError}</ErrorMessage>}
      <FormField id={`${idPrefix}-name`} label="Name" error={errors.name} hint={`${NAME_MIN}–${NAME_MAX} characters.`}>
        {(control) => (
          <input {...control} value={name} onChange={(event) => setName(event.target.value)} disabled={pending} />
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
            rows={3}
            onChange={(event) => setDescription(event.target.value)}
            disabled={pending}
          />
        )}
      </FormField>
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
