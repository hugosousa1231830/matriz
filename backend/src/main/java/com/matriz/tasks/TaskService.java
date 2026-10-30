package com.matriz.tasks;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.identity.UserDirectory;
import com.matriz.projects.ProjectAccessPolicy;
import com.matriz.projects.ProjectAction;
import com.matriz.projects.ProjectMembership;
import com.matriz.shared.ApiException;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;
import com.matriz.shared.ProblemCode;
import com.matriz.shared.Timestamps;

/**
 * Task use cases. Every operation is authorized by {@link ProjectAccessPolicy}
 * with the session's user ({@code 404} → {@code 403} → {@code 409
 * PROJECT_ARCHIVED}); the creator and assigner are always taken from the
 * session.
 */
@Service
public class TaskService {

	private final TaskRepository tasks;

	private final TaskQueries queries;

	private final ProjectAccessPolicy policy;

	private final ProjectMembership membership;

	private final UserDirectory users;

	private final JdbcClient jdbc;

	private final Clock clock;

	TaskService(TaskRepository tasks, TaskQueries queries, ProjectAccessPolicy policy, ProjectMembership membership,
			UserDirectory users, JdbcClient jdbc, Clock clock) {
		this.tasks = tasks;
		this.queries = queries;
		this.policy = policy;
		this.membership = membership;
		this.users = users;
		this.jdbc = jdbc;
		this.clock = clock;
	}

	/**
	 * Managers and Administrators. Status starts as {@code TODO}; each assignee
	 * must be an active member of the project ({@code 422 ASSIGNEE_NOT_ELIGIBLE}).
	 */
	@Transactional
	public TaskView create(AuthenticatedUser user, UUID projectId, String title, String description,
			TaskPriority priority, List<UUID> assigneeIds) {
		policy.require(projectId, user, ProjectAction.CREATE_TASK);
		requireEligible(projectId, assigneeIds);
		Instant now = now();
		Task task = tasks.saveAndFlush(new Task(UUID.randomUUID(), projectId, title, description, priority, user.id(),
				now));
		for (UUID assigneeId : assigneeIds) {
			jdbc.sql("""
					INSERT INTO task_assignee (task_id, user_id, assigned_at, assigned_by_id)
					VALUES (:taskId, :userId, :now, :by)""")
				.param("taskId", task.getId())
				.param("userId", assigneeId)
				.param("now", now.atOffset(ZoneOffset.UTC))
				.param("by", user.id())
				.update();
		}
		return view(task.getId());
	}

	/** Any member of the project and Administrators. */
	@Transactional(readOnly = true)
	public PageResponse<TaskView> list(AuthenticatedUser user, UUID projectId, TaskFilter filter, Paging paging) {
		policy.require(projectId, user, ProjectAction.VIEW);
		return queries.list(projectId, filter, paging);
	}

	@Transactional(readOnly = true)
	public TaskView get(AuthenticatedUser user, UUID taskId) {
		authorize(user, taskId, ProjectAction.VIEW);
		return view(taskId);
	}

	/**
	 * Managers, Administrators and the task's assignees. A {@code version}
	 * different from the current one gives {@code 409 VERSION_CONFLICT}
	 * (ADR-0005); a successful update increments it.
	 */
	@Transactional
	public TaskView update(AuthenticatedUser user, UUID taskId, TaskChanges changes) {
		authorize(user, taskId, ProjectAction.EDIT_TASK);
		Task task = tasks.findById(taskId).orElseThrow(ApiException::notFound);
		if (task.getVersion() != changes.version()) {
			throw versionConflict();
		}
		task.apply(changes, now());
		try {
			tasks.flush();
		}
		catch (ObjectOptimisticLockingFailureException ex) {
			// Updated concurrently by another request after the check above.
			throw versionConflict();
		}
		return view(taskId);
	}

	/** Hard delete by Managers and Administrators; assignments and comments cascade (ADR-0014). */
	@Transactional
	public void delete(AuthenticatedUser user, UUID taskId) {
		authorize(user, taskId, ProjectAction.DELETE_TASK);
		tasks.deleteById(taskId);
		tasks.flush();
	}

	/**
	 * Managers and Administrators assign an active member of the task's project
	 * ({@code 409 ALREADY_ASSIGNED} if already assigned, checked first;
	 * {@code 422 ASSIGNEE_NOT_ELIGIBLE} otherwise). The task {@code version} and
	 * {@code updatedAt} are not changed (as stated in the contract).
	 */
	@Transactional
	public TaskView assign(AuthenticatedUser user, UUID taskId, UUID userId) {
		UUID projectId = authorize(user, taskId, ProjectAction.ASSIGN);
		if (isAssigned(taskId, userId)) {
			throw alreadyAssigned();
		}
		requireEligible(projectId, List.of(userId));
		int inserted = jdbc.sql("""
				INSERT INTO task_assignee (task_id, user_id, assigned_at, assigned_by_id)
				VALUES (:taskId, :userId, :now, :by)
				ON CONFLICT (task_id, user_id) DO NOTHING""")
			.param("taskId", taskId)
			.param("userId", userId)
			.param("now", now().atOffset(ZoneOffset.UTC))
			.param("by", user.id())
			.update();
		if (inserted == 0) {
			// Assigned concurrently by another request after the check above.
			throw alreadyAssigned();
		}
		return view(taskId);
	}

	/** Managers and Administrators; {@code 404} when the user is not assigned to the task. */
	@Transactional
	public void unassign(AuthenticatedUser user, UUID taskId, UUID userId) {
		authorize(user, taskId, ProjectAction.ASSIGN);
		int deleted = jdbc.sql("DELETE FROM task_assignee WHERE task_id = :taskId AND user_id = :userId")
			.param("taskId", taskId)
			.param("userId", userId)
			.update();
		if (deleted == 0) {
			throw ApiException.notFound();
		}
	}

	/** Authorizes {@code action} on the task and returns its project. */
	private UUID authorize(AuthenticatedUser user, UUID taskId, ProjectAction action) {
		UUID projectId = queries.projectOf(taskId).orElseThrow(ApiException::notFound);
		policy.requireTask(projectId, taskId, user, action);
		return projectId;
	}

	private boolean isAssigned(UUID taskId, UUID userId) {
		return jdbc.sql("SELECT EXISTS (SELECT 1 FROM task_assignee WHERE task_id = :taskId AND user_id = :userId)")
			.param("taskId", taskId)
			.param("userId", userId)
			.query(Boolean.class)
			.single();
	}

	private static ApiException alreadyAssigned() {
		return new ApiException(ProblemCode.ALREADY_ASSIGNED, "The user is already assigned to this task.");
	}

	private void requireEligible(UUID projectId, List<UUID> assigneeIds) {
		boolean ineligible = !membership.nonMembers(projectId, assigneeIds).isEmpty()
				|| assigneeIds.stream().anyMatch(id -> users.findActive(id).isEmpty());
		if (ineligible) {
			throw new ApiException(ProblemCode.ASSIGNEE_NOT_ELIGIBLE,
					"Assignees must be active members of the project.");
		}
	}

	private TaskView view(UUID taskId) {
		return queries.find(taskId).orElseThrow(ApiException::notFound);
	}

	private Instant now() {
		return Timestamps.now(clock);
	}

	private static ApiException versionConflict() {
		return new ApiException(ProblemCode.VERSION_CONFLICT,
				"The task was changed by someone else. Reload it and try again.");
	}

	/** Changes requested by a partial update (already trimmed and validated). */
	record TaskChanges(boolean titleSet, String title, boolean descriptionSet, String description, TaskStatus status,
			TaskPriority priority, int version) {
	}

}
