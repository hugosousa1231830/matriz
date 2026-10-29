package com.matriz.tasks;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import jakarta.persistence.Version;

import org.springframework.data.domain.Persistable;

/**
 * A task ({@code task} table). {@code version} implements optimistic
 * concurrency (ADR-0005): it starts at 1 and every update increments it.
 */
@Entity
@Table(name = "task")
class Task implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "project_id", nullable = false, updatable = false)
	private UUID projectId;

	@Column(nullable = false, length = 150)
	private String title;

	@Column(length = 2000)
	private String description;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TaskStatus status;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private TaskPriority priority;

	@Column(name = "created_by_id", nullable = false, updatable = false)
	private UUID createdById;

	@Version
	@Column(nullable = false)
	private int version;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Transient
	private boolean isNew;

	protected Task() {
	}

	Task(UUID id, UUID projectId, String title, String description, TaskPriority priority, UUID createdById,
			Instant now) {
		this.id = id;
		this.projectId = projectId;
		this.title = title;
		this.description = description;
		this.status = TaskStatus.TODO;
		this.priority = priority;
		this.createdById = createdById;
		this.version = 1;
		this.createdAt = now;
		this.updatedAt = now;
		this.isNew = true;
	}

	@Override
	public UUID getId() {
		return id;
	}

	@Override
	public boolean isNew() {
		return isNew;
	}

	@PostLoad
	@PostPersist
	void markNotNew() {
		isNew = false;
	}

	int getVersion() {
		return version;
	}

	/** Applies a validated partial update; always touches {@code updatedAt}, so the version is incremented. */
	void apply(TaskService.TaskChanges changes, Instant now) {
		if (changes.titleSet()) {
			title = changes.title();
		}
		if (changes.descriptionSet()) {
			description = changes.description();
		}
		if (changes.status() != null) {
			status = changes.status();
		}
		if (changes.priority() != null) {
			priority = changes.priority();
		}
		updatedAt = now;
	}

}
