import { useState, type FormEvent } from 'react'
import { Navigate, useLocation } from 'react-router'
import { ApiError } from '../api/auth'
import { isNetworkError } from '../api/errors'
import { useAuth } from '../auth/AuthContext'
import { useDocumentTitle } from '../hooks'

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+$/

interface FieldErrors {
  email?: string
  password?: string
}

function validate(email: string, password: string): FieldErrors {
  const errors: FieldErrors = {}
  if (!email.trim()) {
    errors.email = 'Email is required.'
  } else if (!EMAIL_PATTERN.test(email.trim()) || email.trim().length > 254) {
    errors.email = 'Enter a valid email address.'
  }
  if (!password) {
    errors.password = 'Password is required.'
  } else if (password.length > 128) {
    errors.password = 'Password must have at most 128 characters.'
  }
  return errors
}

function signInErrorMessage(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 401) {
      return 'Invalid email or password.'
    }
    if (error.status === 429) {
      return 'Too many sign-in attempts. Please wait a minute and try again.'
    }
  }
  if (isNetworkError(error)) {
    return 'Cannot reach the server. Check your connection and try again.'
  }
  return 'Sign-in failed. Please try again.'
}

export function SignInPage() {
  const { user, sessionExpired, signIn } = useAuth()
  const location = useLocation()
  useDocumentTitle('Sign in')
  const from = (location.state as { from?: string } | null)?.from ?? '/'

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState<string>()
  const [pending, setPending] = useState(false)

  if (user) {
    return <Navigate to={from} replace />
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const errors = validate(email, password)
    setFieldErrors(errors)
    setFormError(undefined)
    if (errors.email || errors.password) {
      return
    }
    setPending(true)
    try {
      // On success the signed-in user re-renders this page into a redirect.
      await signIn(email.trim(), password)
    } catch (error) {
      setFormError(signInErrorMessage(error))
      setPassword('')
      setPending(false)
    }
  }

  return (
    <main className="sign-in-page">
      <div className="sign-in-card">
        <p className="sign-in-brand">
          <span className="logo-mark" aria-hidden="true">
            <i />
            <i />
            <i />
            <i />
          </span>
          Matriz
        </p>
        <h1>Sign in</h1>
        <p className="muted sign-in-intro">Welcome back! Sign in to see your projects and tasks.</p>
        {sessionExpired && !formError && (
          <p role="status" className="notice">
            Your session has expired. Please sign in again.
          </p>
        )}
        {formError && (
          <p role="alert" className="error">
            {formError}
          </p>
        )}
        <form onSubmit={handleSubmit} noValidate>
          <div className="form-field">
            <label htmlFor="email">Email</label>
            <input
              id="email"
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              aria-invalid={Boolean(fieldErrors.email)}
              aria-describedby={fieldErrors.email ? 'email-error' : undefined}
              disabled={pending}
            />
            {fieldErrors.email && (
              <p id="email-error" className="field-error">
                {fieldErrors.email}
              </p>
            )}
          </div>
          <div className="form-field">
            <label htmlFor="password">Password</label>
            <input
              id="password"
              type="password"
              autoComplete="current-password"
              value={password}
              onChange={(event) => setPassword(event.target.value)}
              aria-invalid={Boolean(fieldErrors.password)}
              aria-describedby={fieldErrors.password ? 'password-error' : undefined}
              disabled={pending}
            />
            {fieldErrors.password && (
              <p id="password-error" className="field-error">
                {fieldErrors.password}
              </p>
            )}
          </div>
          <button type="submit" disabled={pending}>
            {pending ? 'Signing in…' : 'Sign in'}
          </button>
        </form>
      </div>
    </main>
  )
}
