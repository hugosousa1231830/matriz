package com.matriz.tasks;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A task as returned by the API ({@code Task} in openapi.yaml).
 *
 * @param assignees sorted by name; empty when the task is unassigned
 */
public record TaskView(UUID id, UUID projectId, String title, String description, TaskStatus status,
		TaskPriority priority, List<UserSummary> assignees, UserSummary createdBy, int version, Instant createdAt,
		Instant updatedAt) {

	/** {@code UserSummary} in openapi.yaml. */
	public record UserSummary(UUID id, String name) {
	}

}
