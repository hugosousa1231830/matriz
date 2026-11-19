import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { useNavigate } from 'react-router'

/** How long a card shows its pressed state before navigating (matches `.is-pressed` in index.css). */
export const PRESS_DURATION_MS = 140

export function prefersReducedMotion(): boolean {
  return typeof window.matchMedia === 'function' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
}

/**
 * Press-then-navigate for a card whose main link is a stretched `<Link>`:
 * a plain left click (or Enter) shows the pressed state briefly, then navigates in the same tab.
 * Modifier/middle clicks and reduced motion keep the link's default behaviour.
 *
 * Usage: `const { pressed, onClick } = usePressNavigate(to)`; put `onClick` on the `<Link to={to}>`
 * (class `stretched-link`) and add `is-pressed` to the card while `pressed` is true.
 */
export function usePressNavigate(to: string) {
  const navigate = useNavigate()
  const [pressed, setPressed] = useState(false)
  const timer = useRef<number | undefined>(undefined)

  useEffect(() => () => window.clearTimeout(timer.current), [])

  function onClick(event: MouseEvent<HTMLAnchorElement>) {
    if (
      event.defaultPrevented ||
      event.button !== 0 ||
      event.metaKey ||
      event.ctrlKey ||
      event.shiftKey ||
      event.altKey ||
      prefersReducedMotion()
    ) {
      return
    }
    event.preventDefault()
    if (timer.current !== undefined) return
    setPressed(true)
    timer.current = window.setTimeout(() => {
      timer.current = undefined
      setPressed(false)
      navigate(to)
    }, PRESS_DURATION_MS)
  }

  return { pressed, onClick }
}
