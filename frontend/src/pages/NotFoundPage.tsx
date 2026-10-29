import { Link } from 'react-router'
import { useDocumentTitle } from '../hooks'

/** Unknown address inside the signed-in area. */
export function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <>
      <h1>Page not found</h1>
      <p>This address does not match any page.</p>
      <p>
        <Link to="/">Go to the dashboard</Link> or <Link to="/projects">browse projects</Link>.
      </p>
    </>
  )
}
