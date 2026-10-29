import type { ReactNode } from 'react'
import { Navigate, useLocation } from 'react-router'
import { useAuth } from './AuthContext'

/** Renders its children only with a valid session; otherwise redirects to sign-in. */
export function RequireAuth({ children }: { children: ReactNode }) {
  const { user } = useAuth()
  const location = useLocation()

  if (user === undefined) {
    return <p role="status">Loading…</p>
  }
  if (user === null) {
    return <Navigate to="/sign-in" replace state={{ from: location.pathname }} />
  }
  return children
}
