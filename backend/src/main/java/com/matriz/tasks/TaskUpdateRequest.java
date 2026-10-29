package com.matriz.tasks;

import com.matriz.shared.Text;

/**
 * {@code TaskUpdateRequest}: a partial update that distinguishes an absent
 * property from an explicit {@code null} (which clears the description).
 * Values are trimmed; validation is in {@link TaskRules#validate}.
 */
class TaskUpdateRequest {

	private String title;

	private boolean titleSet;

	private String description;

	private boolean descriptionSet;

	private TaskStatus status;

	private boolean statusSet;

	private TaskPriority priority;

	private boolean prioritySet;

	private Integer version;

	public void setTitle(String title) {
		this.title = Text.trim(title);
		this.titleSet = true;
	}

	public void setDescription(String description) {
		this.description = Text.trimToNull(description);
		this.descriptionSet = true;
	}

	public void setStatus(TaskStatus status) {
		this.status = status;
		this.statusSet = true;
	}

	public void setPriority(TaskPriority priority) {
		this.priority = priority;
		this.prioritySet = true;
	}

	public void setVersion(Integer version) {
		this.version = version;
	}

	String getTitle() {
		return title;
	}

	String getDescription() {
		return description;
	}

	TaskStatus getStatus() {
		return status;
	}

	TaskPriority getPriority() {
		return priority;
	}

	Integer getVersion() {
		return version;
	}

	boolean titleSet() {
		return titleSet;
	}

	boolean descriptionSet() {
		return descriptionSet;
	}

	boolean statusSet() {
		return statusSet;
	}

	boolean prioritySet() {
		return prioritySet;
	}

}
