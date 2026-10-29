import { useState } from 'react'
import { Link, Outlet } from 'react-router'
import { useAuth } from '../auth/AuthContext'

/** Header (user, sign out) around the signed-in pages. */
export function AppLayout() {
  const { user, signOut } = useAuth()
  const [pending, setPending] = useState(false)

  async function handleSignOut() {
    setPending(true)
    // Once signed out, the route guard redirects to the sign-in page.
    await signOut().catch(() => setPending(false))
  }

  return (
    <div className="app-shell">
      <header className="app-header">
        <Link to="/" className="brand">
          Matriz
        </Link>
        <span>
          {user?.name}
          {user?.isAdmin && <span className="badge">Administrator</span>}
        </span>
        <button type="button" onClick={handleSignOut} disabled={pending}>
          Sign out
        </button>
      </header>
      <main>
        <Outlet />
      </main>
    </div>
  )
}
