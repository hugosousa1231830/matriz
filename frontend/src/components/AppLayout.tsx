import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, NavLink, Outlet, useLocation } from 'react-router'
import { useAuth } from '../auth/AuthContext'
import { FlashContext } from '../hooks'
import { Avatar } from './Avatar'

const FLASH_MS = 6000

/** Header (navigation, user, sign out), success messages and the signed-in pages. */
export function AppLayout() {
  const { user, signOut } = useAuth()
  const location = useLocation()
  const [pending, setPending] = useState(false)
  const [message, setMessage] = useState<{ id: number; text: string }>()

  const flash = useCallback((text: string) => setMessage({ id: Date.now(), text }), [])
  const flashState = useMemo(() => ({ flash }), [flash])

  useEffect(() => {
    if (!message) return
    const timer = setTimeout(() => setMessage(undefined), FLASH_MS)
    return () => clearTimeout(timer)
  }, [message])

  async function handleSignOut() {
    setPending(true)
    // Once signed out, the route guard redirects to the sign-in page.
    await signOut().catch(() => setPending(false))
  }

  return (
    <FlashContext.Provider value={flashState}>
      <a href="#main" className="skip-link">
        Skip to main content
      </a>
      <header className="app-header">
        <div className="app-header-inner">
          <Link to="/" className="brand">
            <span className="logo-mark" aria-hidden="true">
              <i />
              <i />
              <i />
              <i />
            </span>
            Matriz
          </Link>
          <nav aria-label="Main" className="main-nav">
            <NavLink to="/" end>
              Dashboard
            </NavLink>
            <NavLink to="/projects">Projects</NavLink>
          </nav>
          <div className="user-area">
            <span className="user">
              {user && <Avatar name={user.name} />}
              <span className="user-name">{user?.name}</span>
              {user?.isAdmin && <span className="badge pill admin">Administrator</span>}
            </span>
            <button type="button" className="secondary" onClick={handleSignOut} disabled={pending}>
              {pending ? 'Signing out…' : 'Sign out'}
            </button>
          </div>
        </div>
      </header>
      <main id="main" tabIndex={-1} className="app-shell">
        <div role="status" className="flash-region">
          {message && (
            <p key={message.id} className="success toast">
              <span className="toast-icon" aria-hidden="true">
                ✓
              </span>
              <span className="toast-text">{message.text}</span>
              <button type="button" className="link-button" onClick={() => setMessage(undefined)}>
                Dismiss
              </button>
            </p>
          )}
        </div>
        <div key={location.pathname} className="page-enter">
          <Outlet />
        </div>
      </main>
    </FlashContext.Provider>
  )
}
