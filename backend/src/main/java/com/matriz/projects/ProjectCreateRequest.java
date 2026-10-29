package com.matriz.projects;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.matriz.shared.Text;

/** {@code ProjectCreateRequest}: the name is trimmed; a blank description is stored as {@code null}. */
record ProjectCreateRequest(
		@NotNull(message = "Name is required.") @Size(min = ProjectRules.NAME_MIN, max = ProjectRules.NAME_MAX,
				message = ProjectRules.NAME_MESSAGE) String name,
		@Size(max = ProjectRules.DESCRIPTION_MAX, message = ProjectRules.DESCRIPTION_MESSAGE) String description) {

	ProjectCreateRequest {
		name = Text.trim(name);
		description = Text.trimToNull(description);
	}

}
