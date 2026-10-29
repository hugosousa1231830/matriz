import { createContext, useContext, useEffect } from 'react'

export interface FlashState {
  /** Shows a short success message in the page header area (replaces any previous one). */
  flash: (message: string) => void
}

export const FlashContext = createContext<FlashState>({ flash: () => {} })

export function useFlash(): FlashState['flash'] {
  return useContext(FlashContext).flash
}

/** Sets the browser tab title to "<title> · Matriz". */
export function useDocumentTitle(title: string | undefined): void {
  useEffect(() => {
    document.title = title ? `${title} · Matriz` : 'Matriz'
  }, [title])
}
