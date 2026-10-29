import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/auth'
import { addComment, COMMENT_PAGE_SIZE, errorMessage, listComments, validateComment } from '../api/comments'

interface Props {
  taskId: string
  /** Any member of the project and Administrators, on an active project. */
  canComment: boolean
}

/** Comments of a task, oldest first, with author and date; members add comments (no edit or delete). */
export function CommentsPanel({ taskId, canComment }: Props) {
  const queryClient = useQueryClient()
  const [page, setPage] = useState(1)
  const [body, setBody] = useState('')
  const [fieldError, setFieldError] = useState<string>()
  const [formError, setFormError] = useState<unknown>()
  const [pending, setPending] = useState(false)

  const comments = useQuery({
    queryKey: ['task', taskId, 'comments', page],
    queryFn: () => listComments(taskId, page),
    placeholderData: keepPreviousData,
  })
  const data = comments.data
  const pageCount = data ? Math.max(1, Math.ceil(data.totalItems / COMMENT_PAGE_SIZE)) : 1

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    const found = validateComment(body)
    setFieldError(found)
    setFormError(undefined)
    if (found) {
      return
    }
    setPending(true)
    try {
      await addComment(taskId, body)
      setBody('')
      // The new comment is the newest: show the last page.
      const total = (data?.totalItems ?? 0) + 1
      setPage(Math.max(1, Math.ceil(total / COMMENT_PAGE_SIZE)))
      await queryClient.invalidateQueries({ queryKey: ['task', taskId, 'comments'] })
    } catch (error) {
      const field = error instanceof ApiError ? error.fieldErrors.find((e) => e.field === 'body') : undefined
      if (field) {
        setFieldError(field.message)
      } else {
        setFormError(error)
      }
    } finally {
      setPending(false)
    }
  }

  return (
    <section aria-labelledby="comments-title" className="panel">
      <h2 id="comments-title">Comments</h2>
      {comments.isPending && <p>Loading comments…</p>}
      {comments.isError && (
        <p role="alert" className="error">
          {errorMessage(comments.error)}
        </p>
      )}
      {data && data.totalItems === 0 && <p className="muted">No comments yet.</p>}
      {data && data.items.length > 0 && (
        <ol className="comment-list" aria-label="Comments">
          {data.items.map((comment) => (
            <li key={comment.id}>
              <p className="muted">
                <strong>{comment.author.name}</strong> ·{' '}
                <time dateTime={comment.createdAt}>{new Date(comment.createdAt).toLocaleString()}</time>
              </p>
              <p className="comment-body">{comment.body}</p>
            </li>
          ))}
        </ol>
      )}
      {pageCount > 1 && (
        <nav className="pagination" aria-label="Comment pages">
          <button type="button" onClick={() => setPage(page - 1)} disabled={page <= 1}>
            Previous
          </button>
          <span>
            Page {page} of {pageCount}
          </span>
          <button type="button" onClick={() => setPage(page + 1)} disabled={page >= pageCount}>
            Next
          </button>
        </nav>
      )}
      {canComment && (
        <form className="comment-form" onSubmit={handleSubmit} noValidate>
          {formError !== undefined && (
            <p role="alert" className="error">
              {errorMessage(formError)}
            </p>
          )}
          <label htmlFor="comment-body">Add a comment</label>
          <textarea
            id="comment-body"
            value={body}
            rows={3}
            onChange={(event) => setBody(event.target.value)}
            aria-invalid={Boolean(fieldError)}
            aria-describedby={fieldError ? 'comment-body-error' : undefined}
            disabled={pending}
          />
          {fieldError && (
            <p id="comment-body-error" className="field-error">
              {fieldError}
            </p>
          )}
          <div className="actions">
            <button type="submit" disabled={pending}>
              {pending ? 'Posting…' : 'Post comment'}
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
