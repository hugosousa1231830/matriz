package com.matriz.comments;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** An immutable comment on a task ({@code comment} table); author and date come from the server. */
@Entity
@Table(name = "comment")
class Comment implements Persistable<UUID> {

	@Id
	private UUID id;

	@Column(name = "task_id", nullable = false, updatable = false)
	private UUID taskId;

	@Column(name = "author_id", nullable = false, updatable = false)
	private UUID authorId;

	@Column(nullable = false, updatable = false, length = 2000)
	private String body;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Transient
	private boolean isNew;

	protected Comment() {
	}

	Comment(UUID id, UUID taskId, UUID authorId, String body, Instant createdAt) {
		this.id = id;
		this.taskId = taskId;
		this.authorId = authorId;
		this.body = body;
		this.createdAt = createdAt;
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

	UUID getTaskId() {
		return taskId;
	}

	UUID getAuthorId() {
		return authorId;
	}

	String getBody() {
		return body;
	}

	Instant getCreatedAt() {
		return createdAt;
	}

}
