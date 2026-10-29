package com.matriz.tasks;

import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.projects.ProjectMemberRemoved;

/**
 * Deletes a removed member's assignments on the project's tasks, in the same
 * transaction as the removal (BR7); tasks and comments are kept.
 */
@Component
class AssignmentCleanup {

	private final JdbcClient jdbc;

	AssignmentCleanup(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@EventListener
	@Transactional(propagation = Propagation.MANDATORY)
	void on(ProjectMemberRemoved event) {
		jdbc.sql("""
				DELETE FROM task_assignee a USING task t
				WHERE a.task_id = t.id AND t.project_id = :projectId AND a.user_id = :userId
				""").param("projectId", event.projectId()).param("userId", event.userId()).update();
	}

}
