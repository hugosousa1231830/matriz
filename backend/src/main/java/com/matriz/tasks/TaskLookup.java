package com.matriz.tasks;

import java.util.Optional;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Task facts for the other modules (e.g. comments), so they can authorize an
 * operation on a task with {@code ProjectAccessPolicy.requireTask} without
 * reading the {@code task} table.
 */
@Component
public class TaskLookup {

	private final TaskQueries queries;

	TaskLookup(TaskQueries queries) {
		this.queries = queries;
	}

	/** The project of a task, if the task exists. */
	@Transactional(readOnly = true)
	public Optional<UUID> projectOf(UUID taskId) {
		return queries.projectOf(taskId);
	}

}
