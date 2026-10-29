package com.matriz.projects;

import java.util.UUID;

import com.matriz.shared.ApiException;

/**
 * The caller's access to one project they can see (obtained from
 * {@link ProjectAccessPolicy#access}). Checks are applied in a fixed order:
 * visibility ({@code 404}, already done) → permission ({@code 403
 * FORBIDDEN}) → archived state ({@code 409 PROJECT_ARCHIVED}).
 *
 * @param projectId the project
 * @param role      the caller's role in the project, {@code null} for an Administrator who is not a member
 * @param admin     whether the caller is a platform Administrator
 * @param archived  whether the project is archived (read-only)
 */
public record ProjectAccess(UUID projectId, ProjectRole role, boolean admin, boolean archived) {

	public enum Decision {

		ALLOWED, FORBIDDEN, ARCHIVED

	}

	public boolean isManager() {
		return admin || role == ProjectRole.MANAGER;
	}

	public boolean isMember() {
		return role != null;
	}

	/** Decision for {@code action}; {@code taskAssignee} = the caller is assigned to the task concerned. */
	public Decision decide(ProjectAction action, boolean taskAssignee) {
		boolean permitted = switch (action) {
			case VIEW, COMMENT -> true;
			case EDIT_TASK -> isManager() || (taskAssignee && isMember());
			case EDIT_PROJECT, ARCHIVE, UNARCHIVE, MANAGE_MEMBERS, CREATE_TASK, DELETE_TASK, ASSIGN -> isManager();
		};
		if (!permitted) {
			return Decision.FORBIDDEN;
		}
		// Archive is idempotent (200 unchanged) and unarchive is the way back.
		if (archived && action.isWrite() && action != ProjectAction.ARCHIVE && action != ProjectAction.UNARCHIVE) {
			return Decision.ARCHIVED;
		}
		return Decision.ALLOWED;
	}

	public boolean can(ProjectAction action) {
		return decide(action, false) == Decision.ALLOWED;
	}

	/** Throws {@code 403}/{@code 409} unless {@code action} is allowed. */
	public ProjectAccess require(ProjectAction action) {
		return require(action, false);
	}

	/** Like {@link #require(ProjectAction)}, for task actions where being an assignee matters ({@code EDIT_TASK}). */
	public ProjectAccess require(ProjectAction action, boolean taskAssignee) {
		return switch (decide(action, taskAssignee)) {
			case ALLOWED -> this;
			case FORBIDDEN -> throw ApiException.forbidden();
			case ARCHIVED -> throw ApiException.projectArchived();
		};
	}

}
