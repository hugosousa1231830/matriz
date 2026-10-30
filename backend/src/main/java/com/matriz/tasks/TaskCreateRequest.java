package com.matriz.tasks;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.validator.constraints.CodePointLength;

import com.matriz.shared.Text;

/**
 * {@code TaskCreateRequest}: the title is trimmed; a blank description is
 * stored as {@code null}; priority defaults to {@code MEDIUM}; no assignees by
 * default. Duplicated assignee ids are rejected by {@link TaskController}.
 */
record TaskCreateRequest(
		@NotNull(message = "Title is required.") @CodePointLength(min = TaskRules.TITLE_MIN, max = TaskRules.TITLE_MAX,
				message = TaskRules.TITLE_MESSAGE) String title,
		@CodePointLength(max = TaskRules.DESCRIPTION_MAX, message = TaskRules.DESCRIPTION_MESSAGE) String description,
		TaskPriority priority,
		@Size(max = TaskRules.ASSIGNEES_MAX, message = "At most 50 assignees.") List<@NotNull(
				message = "Assignee ids must not be null.") UUID> assigneeIds) {

	TaskCreateRequest {
		title = Text.trim(title);
		description = Text.trimToNull(description);
		priority = priority == null ? TaskPriority.MEDIUM : priority;
		assigneeIds = assigneeIds == null ? List.of() : assigneeIds;
	}

}
