package com.matriz.projects;

import java.time.Instant;
import java.util.UUID;

/**
 * A project as returned by the API ({@code Project} in openapi.yaml), with the
 * caller's role and task counts.
 *
 * @param myRole {@code null} for an Administrator who is not a member
 */
public record ProjectView(UUID id, String name, String description, ProjectStatus status, Owner owner,
		ProjectRole myRole, int memberCount, TaskCounts taskCounts, int progressPercent, Instant archivedAt,
		Instant createdAt, Instant updatedAt) {

	/** {@code UserSummary} in openapi.yaml. */
	public record Owner(UUID id, String name) {
	}

}
