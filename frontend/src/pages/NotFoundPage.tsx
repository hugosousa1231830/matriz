import { Link } from 'react-router'
import { useDocumentTitle } from '../hooks'

/** Unknown address inside the signed-in area. */
export function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <section className="not-found">
      <span className="not-found-emoji" aria-hidden="true">
        🧭
      </span>
      <p className="not-found-code">404</p>
      <h1>Page not found</h1>
      <p className="muted">This address does not match any page.</p>
      <p className="actions">
        <Link className="button" to="/">
          Go to the dashboard
        </Link>
        <Link className="button secondary" to="/projects">
          Browse projects
        </Link>
      </p>
    </section>
  )
}
