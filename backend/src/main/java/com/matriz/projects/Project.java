package com.matriz.projects;

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

import org.springframework.data.domain.Persistable;

/** A project ({@code project} table). The owner is immutable in release 1. */
@Entity
@Table(name = "project")
class Project implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(length = 1000)
	private String description;

	@Column(name = "owner_id", nullable = false, updatable = false)
	private UUID ownerId;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectStatus status;

	@Column(name = "archived_at")
	private Instant archivedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	@Transient
	private boolean isNew;

	protected Project() {
	}

	Project(UUID id, String name, String description, UUID ownerId, Instant now) {
		this.id = id;
		this.name = name;
		this.description = description;
		this.ownerId = ownerId;
		this.status = ProjectStatus.ACTIVE;
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

	UUID getOwnerId() {
		return ownerId;
	}

	void rename(String name, Instant now) {
		this.name = name;
		this.updatedAt = now;
	}

	void describe(String description, Instant now) {
		this.description = description;
		this.updatedAt = now;
	}

	/** @return {@code false} when the project was already archived (no change) */
	boolean archive(Instant now) {
		if (status == ProjectStatus.ARCHIVED) {
			return false;
		}
		status = ProjectStatus.ARCHIVED;
		archivedAt = now;
		updatedAt = now;
		return true;
	}

	/** @return {@code false} when the project was already active (no change) */
	boolean unarchive(Instant now) {
		if (status == ProjectStatus.ACTIVE) {
			return false;
		}
		status = ProjectStatus.ACTIVE;
		archivedAt = null;
		updatedAt = now;
		return true;
	}

}
