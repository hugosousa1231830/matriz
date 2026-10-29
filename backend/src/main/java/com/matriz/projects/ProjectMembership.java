package com.matriz.projects;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Membership checks for the other modules (eligible assignees = members of the
 * task's project, R6/R7). Callers must authorize the operation with
 * {@link ProjectAccessPolicy} first.
 */
@Component
public class ProjectMembership {

	private final ProjectMemberRepository members;

	ProjectMembership(ProjectMemberRepository members) {
		this.members = members;
	}

	/** The given users who are not members of the project. */
	@Transactional(readOnly = true)
	public Set<UUID> nonMembers(UUID projectId, Collection<UUID> userIds) {
		Set<UUID> result = new HashSet<>(userIds);
		if (!result.isEmpty()) {
			members.findByIdProjectId(projectId).forEach(m -> result.remove(m.getUserId()));
		}
		return result;
	}

}
