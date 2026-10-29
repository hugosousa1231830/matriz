interface Props {
  /** Accessible name of the navigation landmark, e.g. "Task pages". */
  label: string
  page: number
  pageSize: number
  totalItems: number
  /** Plural noun for the total, e.g. "tasks"; omitted when not given. */
  itemLabel?: string
  onChange: (page: number) => void
}

/** Previous/Next pagination with "Page X of Y (N items)"; hidden when everything fits on one page. */
export function Pagination({ label, page, pageSize, totalItems, itemLabel, onChange }: Props) {
  const pageCount = Math.max(1, Math.ceil(totalItems / pageSize))
  if (pageCount <= 1 && page <= 1) {
    return null
  }
  return (
    <nav aria-label={label} className="pagination">
      <button type="button" disabled={page <= 1} onClick={() => onChange(page - 1)}>
        Previous
      </button>
      <span>
        Page {page} of {pageCount}
        {itemLabel && ` (${totalItems} ${itemLabel})`}
      </span>
      <button type="button" disabled={page >= pageCount} onClick={() => onChange(page + 1)}>
        Next
      </button>
    </nav>
  )
}
