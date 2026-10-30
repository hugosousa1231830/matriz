package com.matriz.tasks;

import java.util.ArrayList;
import java.util.List;

import com.matriz.shared.ApiException;
import com.matriz.shared.Problems;
import com.matriz.shared.Text;

/** Validation limits of task fields (also enforced by check constraints in the schema). */
final class TaskRules {

	static final int TITLE_MIN = 3;

	static final int TITLE_MAX = 150;

	static final int DESCRIPTION_MAX = 2000;

	static final int ASSIGNEES_MAX = 50;

	static final String TITLE_MESSAGE = "Title must have between 3 and 150 characters.";

	static final String DESCRIPTION_MESSAGE = "Description must have at most 2000 characters.";

	private TaskRules() {
	}

	/** Validates a partial update (values already trimmed) and returns the changes. */
	static TaskService.TaskChanges validate(TaskUpdateRequest body) {
		if (body == null) {
			throw ApiException.validation("body", "A request body is required.");
		}
		List<Problems.FieldError> errors = new ArrayList<>();
		if (body.getVersion() == null) {
			errors.add(new Problems.FieldError("version", "Version is required."));
		}
		else if (body.getVersion() < 1) {
			errors.add(new Problems.FieldError("version", "Version must be 1 or greater."));
		}
		if (!body.titleSet() && !body.descriptionSet() && !body.statusSet() && !body.prioritySet()) {
			errors.add(new Problems.FieldError("body",
					"At least one of title, description, status or priority is required."));
		}
		if (body.titleSet()) {
			if (body.getTitle() == null) {
				errors.add(new Problems.FieldError("title", "Title is required."));
			}
			else if (Text.length(body.getTitle()) < TITLE_MIN || Text.length(body.getTitle()) > TITLE_MAX) {
				errors.add(new Problems.FieldError("title", TITLE_MESSAGE));
			}
		}
		if (body.descriptionSet() && body.getDescription() != null
				&& Text.length(body.getDescription()) > DESCRIPTION_MAX) {
			errors.add(new Problems.FieldError("description", DESCRIPTION_MESSAGE));
		}
		if (body.statusSet() && body.getStatus() == null) {
			errors.add(new Problems.FieldError("status", "Status is required."));
		}
		if (body.prioritySet() && body.getPriority() == null) {
			errors.add(new Problems.FieldError("priority", "Priority is required."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		return new TaskService.TaskChanges(body.titleSet(), body.getTitle(), body.descriptionSet(),
				body.getDescription(), body.getStatus(), body.getPriority(), body.getVersion());
	}

}
