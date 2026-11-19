import { keepPreviousData, useQuery, useQueryClient } from '@tanstack/react-query'
import { useState, type FormEvent } from 'react'
import { ApiError } from '../api/auth'
import { POLL_INTERVAL_MS } from '../api/client'
import { addComment, COMMENT_MAX, COMMENT_PAGE_SIZE, errorMessage, listComments, validateComment } from '../api/comments'
import { Avatar } from './Avatar'
import { EmptyState, ErrorMessage, Loading } from './Feedback'
import { FormField } from './FormField'
import { Pagination } from './Pagination'

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
    refetchInterval: POLL_INTERVAL_MS,
  })
  const data = comments.data

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
      {comments.isPending && <Loading>Loading comments…</Loading>}
      {comments.isError && (
        <ErrorMessage onRetry={() => comments.refetch()} retrying={comments.isFetching}>
          {errorMessage(comments.error)}
        </ErrorMessage>
      )}
      {data && data.totalItems === 0 && <EmptyState emoji="💬">No comments yet.</EmptyState>}
      {data && data.items.length > 0 && (
        <ol className="comment-list" aria-label="Comments">
          {data.items.map((comment) => (
            <li key={comment.id}>
              <Avatar name={comment.author.name} size="sm" />
              <div className="comment-bubble">
                <p className="comment-meta">
                  <strong>{comment.author.name}</strong>{' '}
                  <time dateTime={comment.createdAt} className="muted">
                    {new Date(comment.createdAt).toLocaleString()}
                  </time>
                </p>
                <p className="comment-body">{comment.body}</p>
              </div>
            </li>
          ))}
        </ol>
      )}
      {data && (
        <Pagination
          label="Comment pages"
          page={page}
          pageSize={COMMENT_PAGE_SIZE}
          totalItems={data.totalItems}
          itemLabel="comments"
          onChange={setPage}
        />
      )}
      {canComment && (
        <form className="comment-form" onSubmit={handleSubmit} noValidate>
          {formError !== undefined && <ErrorMessage>{errorMessage(formError)}</ErrorMessage>}
          <FormField id="comment-body" label="Add a comment" error={fieldError} hint={`Up to ${COMMENT_MAX} characters.`}>
            {(control) => (
              <textarea
                {...control}
                value={body}
                rows={3}
                onChange={(event) => setBody(event.target.value)}
                disabled={pending}
              />
            )}
          </FormField>
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
