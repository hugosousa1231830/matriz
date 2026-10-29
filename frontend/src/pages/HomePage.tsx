import { useState } from 'react'
import { useAuth } from '../auth/AuthContext'

/** Authenticated landing page; the project list is added with project management. */
export function HomePage() {
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
        <strong>Matriz</strong>
        <span>
          {user?.name}
          {user?.isAdmin && <span className="badge">Administrator</span>}
        </span>
        <button type="button" onClick={handleSignOut} disabled={pending}>
          Sign out
        </button>
      </header>
      <main>
        <h1>Projects</h1>
        <p>Signed in as {user?.email}.</p>
      </main>
    </div>
  )
}
