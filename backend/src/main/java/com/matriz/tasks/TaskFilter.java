package com.matriz.tasks;

import java.util.Set;
import java.util.UUID;

/**
 * Validated filters and sort of the task list.
 *
 * @param assigneeId only tasks assigned to this user ({@code null}: any)
 * @param unassigned only tasks without assignees ({@code assigneeId=none})
 */
record TaskFilter(Set<TaskStatus> statuses, Set<TaskPriority> priorities, UUID assigneeId, boolean unassigned,
		TaskSort.Order sort) {

}
