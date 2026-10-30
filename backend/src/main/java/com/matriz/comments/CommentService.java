package com.matriz.comments;

import java.time.Clock;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.projects.ProjectAccessPolicy;
import com.matriz.projects.ProjectAction;
import com.matriz.shared.ApiException;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;
import com.matriz.shared.Timestamps;
import com.matriz.tasks.TaskLookup;

/**
 * Comment use cases, authorized by {@link ProjectAccessPolicy} on the task's
 * project ({@code 404} → {@code 403} → {@code 409 PROJECT_ARCHIVED}). The
 * author and date always come from the session and the server clock;
 * comments are never edited or deleted (they go with their task).
 */
@Service
class CommentService {

	private final CommentRepository comments;

	private final CommentQueries queries;

	private final TaskLookup tasks;

	private final ProjectAccessPolicy policy;

	private final Clock clock;

	CommentService(CommentRepository comments, CommentQueries queries, TaskLookup tasks, ProjectAccessPolicy policy,
			Clock clock) {
		this.comments = comments;
		this.queries = queries;
		this.tasks = tasks;
		this.policy = policy;
		this.clock = clock;
	}

	/** Any member of the project and Administrators; also on archived projects. */
	@Transactional(readOnly = true)
	PageResponse<CommentView> list(AuthenticatedUser user, UUID taskId, Paging paging) {
		authorize(user, taskId, ProjectAction.VIEW);
		return queries.list(taskId, paging);
	}

	/** Any member of the project and Administrators; not on archived projects. */
	@Transactional
	CommentView create(AuthenticatedUser user, UUID taskId, String body) {
		authorize(user, taskId, ProjectAction.COMMENT);
		Comment comment = comments.saveAndFlush(
				new Comment(UUID.randomUUID(), taskId, user.id(), body, Timestamps.now(clock)));
		return queries.find(comment.getId()).orElseThrow(ApiException::notFound);
	}

	private void authorize(AuthenticatedUser user, UUID taskId, ProjectAction action) {
		UUID projectId = tasks.projectOf(taskId).orElseThrow(ApiException::notFound);
		policy.requireTask(projectId, taskId, user, action);
	}

}
