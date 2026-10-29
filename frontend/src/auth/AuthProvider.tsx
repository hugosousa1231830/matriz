import { useQuery, useQueryClient } from '@tanstack/react-query'
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { onUnauthorized } from '../api/client'
import { AuthContext, CURRENT_USER_KEY, type AuthState } from './AuthContext'

/**
 * Holds the signed-in user (from `GET /auth/me`). Any `401` from the API while
 * signed in means the session expired or was invalidated: cached data is
 * dropped and the user is sent back to sign in with an explanation.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient()
  const [sessionExpired, setSessionExpired] = useState(false)
  const { data: user, isError } = useQuery({
    queryKey: CURRENT_USER_KEY,
    queryFn: authApi.fetchCurrentUser,
    retry: false,
    staleTime: 60_000,
  })

  const clearSession = useCallback(() => {
    queryClient.removeQueries({ predicate: (query) => query.queryKey[0] !== CURRENT_USER_KEY[0] })
    queryClient.setQueryData(CURRENT_USER_KEY, null)
  }, [queryClient])

  useEffect(
    () =>
      onUnauthorized(() => {
        if (queryClient.getQueryData(CURRENT_USER_KEY)) {
          setSessionExpired(true)
        }
        clearSession()
      }),
    [queryClient, clearSession],
  )

  const signIn = useCallback(
    async (email: string, password: string) => {
      const signedIn = await authApi.signIn(email, password)
      clearSession()
      queryClient.setQueryData(CURRENT_USER_KEY, signedIn)
      setSessionExpired(false)
      return signedIn
    },
    [queryClient, clearSession],
  )

  const signOut = useCallback(async () => {
    try {
      await authApi.signOut()
    } finally {
      setSessionExpired(false)
      clearSession()
    }
  }, [clearSession])

  const value = useMemo<AuthState>(
    // If the session check itself fails (e.g. API unreachable), treat as signed out.
    () => ({ user: isError ? null : user, sessionExpired, signIn, signOut }),
    [user, isError, sessionExpired, signIn, signOut],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
