import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { act, render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { CURRENT_USER_KEY } from './auth/AuthContext'
import { ANA, FakeApi, VALID_PASSWORD } from './test/fakeApi'

let fakeApi: FakeApi
let queryClient: QueryClient

function renderApp(path = '/') {
  queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } })
  return render(
    <QueryClientProvider client={queryClient}>
      <MemoryRouter initialEntries={[path]}>
        <App />
      </MemoryRouter>
    </QueryClientProvider>,
  )
}

async function fillAndSubmit(email: string, password: string) {
  const user = userEvent.setup()
  if (email) await user.type(screen.getByLabelText('Email'), email)
  if (password) await user.type(screen.getByLabelText('Password'), password)
  await user.click(screen.getByRole('button', { name: 'Sign in' }))
}

beforeEach(() => {
  fakeApi = new FakeApi()
  vi.stubGlobal('fetch', fakeApi.fetch)
})

afterEach(() => {
  vi.unstubAllGlobals()
  document.cookie = 'XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/'
})

describe('authentication', () => {
  it('redirects to sign-in when there is no session', async () => {
    renderApp('/')

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.queryByText(/session has expired/)).not.toBeInTheDocument()
  })

  it('validates the form before calling the API', async () => {
    renderApp('/sign-in')
    await screen.findByRole('heading', { name: 'Sign in' })

    await fillAndSubmit('', '')
    expect(screen.getByText('Email is required.')).toBeInTheDocument()
    expect(screen.getByText('Password is required.')).toBeInTheDocument()

    await fillAndSubmit('not-an-email', 'x')
    expect(screen.getByText('Enter a valid email address.')).toBeInTheDocument()
    expect(fakeApi.calls('POST', '/auth/sign-in')).toHaveLength(0)
  })

  it('shows a generic message for invalid credentials and stays on sign-in', async () => {
    renderApp('/sign-in')
    await screen.findByRole('heading', { name: 'Sign in' })

    await fillAndSubmit(ANA.email, 'wrong-password')

    expect(await screen.findByRole('alert')).toHaveTextContent('Invalid email or password.')
    expect(screen.getByLabelText('Email')).toHaveValue(ANA.email)
    expect(screen.getByLabelText('Password')).toHaveValue('')
  })

  it('explains throttling', async () => {
    fakeApi.throttled = true
    renderApp('/sign-in')
    await screen.findByRole('heading', { name: 'Sign in' })

    await fillAndSubmit(ANA.email, VALID_PASSWORD)

    expect(await screen.findByRole('alert')).toHaveTextContent('Too many sign-in attempts')
  })

  it('signs in with a CSRF token, shows the user and signs out', async () => {
    renderApp('/')
    await screen.findByRole('heading', { name: 'Sign in' })

    await fillAndSubmit(ANA.email, VALID_PASSWORD)

    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()
    expect(screen.getByText(ANA.name)).toBeInTheDocument()
    const [signIn] = fakeApi.calls('POST', '/auth/sign-in')
    expect(signIn.headers.get('X-XSRF-TOKEN')).toBe('test-csrf-token')
    expect(fakeApi.calls('GET', '/auth/csrf')).toHaveLength(1)

    await userEvent.setup().click(screen.getByRole('button', { name: 'Sign out' }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(fakeApi.user).toBeNull()
    expect(screen.queryByText(/session has expired/)).not.toBeInTheDocument()
  })

  it('returns to sign-in with a clear message when the session expires', async () => {
    fakeApi.user = ANA
    renderApp('/')
    expect(await screen.findByRole('heading', { name: 'Dashboard' })).toBeInTheDocument()

    fakeApi.user = null
    await act(() => queryClient.invalidateQueries({ queryKey: CURRENT_USER_KEY }))

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument()
    expect(screen.getByRole('status')).toHaveTextContent('Your session has expired. Please sign in again.')
  })
})
