import { createContext, useContext } from 'react'
import type { CurrentUser } from '../api/auth'

export interface AuthState {
  /** `undefined` while the session is being checked, `null` when signed out. */
  user: CurrentUser | null | undefined
  /** The session ended (expired or invalidated) while the user was signed in. */
  sessionExpired: boolean
  signIn: (email: string, password: string) => Promise<CurrentUser>
  signOut: () => Promise<void>
}

/** Query key of the signed-in user (`GET /auth/me`). */
export const CURRENT_USER_KEY = ['auth', 'me'] as const

export const AuthContext = createContext<AuthState | null>(null)

export function useAuth(): AuthState {
  const auth = useContext(AuthContext)
  if (!auth) {
    throw new Error('useAuth must be used inside <AuthProvider>')
  }
  return auth
}
