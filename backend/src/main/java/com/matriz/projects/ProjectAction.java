package com.matriz.projects;

/**
 * Actions on a project and its contents, as listed in the access matrix of
 * {@code docs/implementation-plan.md} (§4).
 */
public enum ProjectAction {

	/** Read the project, its members, tasks, assignees and comments. */
	VIEW(false),
	/** Edit the project name and description. */
	EDIT_PROJECT(true),
	ARCHIVE(true),
	/** The only write allowed on an archived project. */
	UNARCHIVE(true),
	/** Search candidates, add members, change roles, remove members. */
	MANAGE_MEMBERS(true),
	CREATE_TASK(true),
	DELETE_TASK(true),
	/** Edit title, description, status and priority of a task (Managers, or assignees of that task). */
	EDIT_TASK(true),
	/** Assign and unassign users. */
	ASSIGN(true),
	COMMENT(true);

	private final boolean write;

	ProjectAction(boolean write) {
		this.write = write;
	}

	public boolean isWrite() {
		return write;
	}

}
