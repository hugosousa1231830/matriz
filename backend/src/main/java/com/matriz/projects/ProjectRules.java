package com.matriz.projects;

import java.util.ArrayList;
import java.util.List;

import com.matriz.shared.ApiException;
import com.matriz.shared.Problems;

/** Validation limits of project fields (also enforced by check constraints in the schema). */
final class ProjectRules {

	static final int NAME_MIN = 3;

	static final int NAME_MAX = 100;

	static final int DESCRIPTION_MAX = 1000;

	static final String NAME_MESSAGE = "Name must have between 3 and 100 characters.";

	static final String DESCRIPTION_MESSAGE = "Description must have at most 1000 characters.";

	private ProjectRules() {
	}

	/** Validates a partial update (values already trimmed) and returns the changes. */
	static ProjectService.ProjectChanges validate(ProjectUpdateRequest body) {
		List<Problems.FieldError> errors = new ArrayList<>();
		if (body == null || (!body.nameSet() && !body.descriptionSet())) {
			throw ApiException.validation("body", "At least one of name or description is required.");
		}
		if (body.nameSet()) {
			if (body.getName() == null) {
				errors.add(new Problems.FieldError("name", "Name is required."));
			}
			else if (body.getName().length() < NAME_MIN || body.getName().length() > NAME_MAX) {
				errors.add(new Problems.FieldError("name", NAME_MESSAGE));
			}
		}
		if (body.descriptionSet() && body.getDescription() != null
				&& body.getDescription().length() > DESCRIPTION_MAX) {
			errors.add(new Problems.FieldError("description", DESCRIPTION_MESSAGE));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		return new ProjectService.ProjectChanges(body.nameSet(), body.getName(), body.descriptionSet(),
				body.getDescription());
	}

}
