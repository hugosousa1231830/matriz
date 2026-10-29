package com.matriz.projects;

import com.matriz.shared.Text;

/**
 * {@code ProjectUpdateRequest}: a partial update that distinguishes an absent
 * property from an explicit {@code null} (which clears the description).
 * Values are trimmed; validation is in {@link ProjectRules#validate}.
 */
class ProjectUpdateRequest {

	private String name;

	private boolean nameSet;

	private String description;

	private boolean descriptionSet;

	public void setName(String name) {
		this.name = Text.trim(name);
		this.nameSet = true;
	}

	public void setDescription(String description) {
		this.description = Text.trimToNull(description);
		this.descriptionSet = true;
	}

	String getName() {
		return name;
	}

	String getDescription() {
		return description;
	}

	boolean nameSet() {
		return nameSet;
	}

	boolean descriptionSet() {
		return descriptionSet;
	}

}
