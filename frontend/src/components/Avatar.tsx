/** Background colours that all keep white initials above the WCAG AA contrast ratio (4.5:1). */
const AVATAR_COLOURS = ['#4f46e5', '#7c3aed', '#be185d', '#b91c1c', '#c2410c', '#047857', '#0e7490', '#1d4ed8']

function initials(name: string): string {
  const parts = name.trim().split(/\s+/).filter(Boolean)
  if (parts.length === 0) return '?'
  const first = parts[0][0]
  const last = parts.length > 1 ? parts[parts.length - 1][0] : ''
  return (first + last).toUpperCase()
}

/** Picks the same colour for the same name, every time. */
function avatarColour(name: string): string {
  let hash = 0
  for (const char of name) hash = (hash * 31 + char.codePointAt(0)!) >>> 0
  return AVATAR_COLOURS[hash % AVATAR_COLOURS.length]
}

interface AvatarProps {
  name: string
  size?: 'sm' | 'md'
  /** Tooltip text; the avatar stays hidden from assistive technology (pair it with visible text). */
  title?: string
}

/** A coloured circle with the person's initials (decorative). */
export function Avatar({ name, size = 'md', title }: AvatarProps) {
  return (
    <span
      className={`avatar avatar-${size}`}
      style={{ backgroundColor: avatarColour(name) }}
      aria-hidden="true"
      title={title}
    >
      {initials(name)}
    </span>
  )
}

interface AvatarStackProps {
  names: string[]
  /** Avatars shown before collapsing the rest into "+k". */
  max?: number
  size?: 'sm' | 'md'
}

/** Overlapping avatars for a list of people (decorative). */
export function AvatarStack({ names, max = 3, size = 'sm' }: AvatarStackProps) {
  if (names.length === 0) return null
  const shown = names.slice(0, max)
  const rest = names.length - shown.length
  return (
    <span className="avatar-stack" aria-hidden="true">
      {shown.map((name, index) => (
        <Avatar key={`${name}-${index}`} name={name} size={size} title={name} />
      ))}
      {rest > 0 && <span className={`avatar avatar-${size} avatar-more`}>+{rest}</span>}
    </span>
  )
}
