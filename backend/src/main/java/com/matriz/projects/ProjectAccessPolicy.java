package com.matriz.projects;

import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.shared.ApiException;

/**
 * Server-side authorization for everything inside a project (projects,
 * members, tasks, assignees, comments). Every operation on a project resource
 * must start here, with the identity taken from the session:
 *
 * <pre>
 * policy.require(projectId, user, ProjectAction.CREATE_TASK);
 * </pre>
 *
 * A project the caller cannot see (it does not exist, or the caller is neither a
 * member nor an Administrator) is reported as {@code 404 NOT_FOUND}, so its
 * existence is not disclosed; see {@link ProjectAccess} for {@code 403} and
 * {@code 409}.
 */
@Component
public class ProjectAccessPolicy {

	private final JdbcClient jdbc;

	ProjectAccessPolicy(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** The caller's access to a visible project; {@code 404} otherwise. */
	@Transactional(readOnly = true)
	public ProjectAccess access(UUID projectId, AuthenticatedUser user) {
		return find(projectId, user).orElseThrow(ApiException::notFound);
	}

	/** Shortcut for {@code access(projectId, user).require(action)}. */
	public ProjectAccess require(UUID projectId, AuthenticatedUser user, ProjectAction action) {
		return access(projectId, user).require(action);
	}

	/**
	 * Authorizes {@code action} on a task: {@code 404} unless the project is
	 * visible and the task belongs to it, then {@code 403}/{@code 409} as for
	 * the project. Whether the caller is an assignee of the task (which allows
	 * {@code EDIT_TASK} for Members) is read from the database, never from the request.
	 */
	@Transactional(readOnly = true)
	public ProjectAccess requireTask(UUID projectId, UUID taskId, AuthenticatedUser user, ProjectAction action) {
		ProjectAccess access = access(projectId, user);
		Boolean assignee = jdbc.sql("""
				SELECT EXISTS (SELECT 1 FROM task_assignee a WHERE a.task_id = t.id AND a.user_id = :userId)
				FROM task t
				WHERE t.id = :taskId AND t.project_id = :projectId""")
			.param("taskId", taskId)
			.param("projectId", projectId)
			.param("userId", user.id())
			.query(Boolean.class)
			.optional()
			.orElseThrow(ApiException::notFound);
		return access.require(action, assignee);
	}

	/** The caller's access, or empty when the project is not visible to them. */
	@Transactional(readOnly = true)
	public Optional<ProjectAccess> find(UUID projectId, AuthenticatedUser user) {
		return jdbc.sql("""
				SELECT p.status, m.role
				FROM project p
				LEFT JOIN project_member m ON m.project_id = p.id AND m.user_id = :userId
				WHERE p.id = :projectId""")
			.param("projectId", projectId)
			.param("userId", user.id())
			.query((rs, row) -> {
				String role = rs.getString("role");
				return new ProjectAccess(projectId, role == null ? null : ProjectRole.valueOf(role), user.admin(),
						"ARCHIVED".equals(rs.getString("status")));
			})
			.optional()
			.filter(access -> access.admin() || access.isMember());
	}

}
