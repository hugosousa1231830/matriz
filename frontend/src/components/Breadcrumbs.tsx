import { Fragment } from 'react'
import { Link } from 'react-router'

export interface Crumb {
  label: string
  /** Omitted for the current page. */
  to?: string
}

/** "Projects › Website Redesign" trail; the last crumb is the current page. */
export function Breadcrumbs({ items }: { items: Crumb[] }) {
  return (
    <nav aria-label="Breadcrumb" className="breadcrumbs">
      {items.map((item, index) => (
        <Fragment key={index}>
          {index > 0 && <span aria-hidden="true"> › </span>}
          {item.to ? (
            <Link to={item.to}>{item.label}</Link>
          ) : (
            <span aria-current="page">{item.label}</span>
          )}
        </Fragment>
      ))}
    </nav>
  )
}
