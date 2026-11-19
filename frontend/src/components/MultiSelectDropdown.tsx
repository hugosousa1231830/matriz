import { useEffect, useId, useRef, useState, type FocusEvent, type KeyboardEvent } from 'react'

interface Option<T extends string> {
  value: T
  label: string
}

interface Props<T extends string> {
  label: string
  options: readonly Option<T>[]
  selected: readonly T[]
  onToggle: (value: T) => void
}

/** "Any" for no selection, the label for one, otherwise how many are selected. */
function selectionSummary<T extends string>(options: readonly Option<T>[], selected: readonly T[]): string {
  if (selected.length === 0) return 'Any'
  if (selected.length === 1) return options.find((option) => option.value === selected[0])?.label ?? '1 selected'
  return `${selected.length} selected`
}

/** A select-like button that opens a panel of checkboxes, for filters that accept several values. */
export function MultiSelectDropdown<T extends string>({ label, options, selected, onToggle }: Props<T>) {
  const id = useId()
  const panelId = `${id}-panel`
  const [open, setOpen] = useState(false)
  const rootRef = useRef<HTMLDivElement>(null)
  const buttonRef = useRef<HTMLButtonElement>(null)
  const summary = selectionSummary(options, selected)

  useEffect(() => {
    if (!open) return
    function onPointerDown(event: PointerEvent) {
      if (!rootRef.current?.contains(event.target as Node)) setOpen(false)
    }
    document.addEventListener('pointerdown', onPointerDown)
    return () => document.removeEventListener('pointerdown', onPointerDown)
  }, [open])

  function onKeyDown(event: KeyboardEvent) {
    if (event.key === 'Escape' && open) {
      event.stopPropagation()
      setOpen(false)
      buttonRef.current?.focus()
    }
  }

  function onBlur(event: FocusEvent) {
    if (event.relatedTarget && !rootRef.current?.contains(event.relatedTarget as Node)) setOpen(false)
  }

  return (
    <div ref={rootRef} className="toolbar-field multi-select" onKeyDown={onKeyDown} onBlur={onBlur}>
      <span className="toolbar-label" aria-hidden="true">
        {label}
      </span>
      <button
        ref={buttonRef}
        type="button"
        className="multi-select-button"
        aria-label={`${label}: ${summary}`}
        aria-expanded={open}
        aria-controls={panelId}
        onClick={() => setOpen((current) => !current)}
      >
        {summary}
      </button>
      <fieldset id={panelId} className="multi-select-panel" hidden={!open}>
        <legend className="visually-hidden">{label}</legend>
        {options.map((option) => (
          <label key={option.value} className="multi-select-option">
            <input
              type="checkbox"
              checked={selected.includes(option.value)}
              onChange={() => onToggle(option.value)}
            />
            {option.label}
          </label>
        ))}
      </fieldset>
    </div>
  )
}
