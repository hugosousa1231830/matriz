package com.matriz.projects;

import java.io.Serializable;
import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.PostLoad;
import jakarta.persistence.PostPersist;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;

import org.springframework.data.domain.Persistable;

/** Membership of a user in a project with a per-project role ({@code project_member} table). */
@Entity
@Table(name = "project_member")
class ProjectMember implements Persistable<ProjectMember.Key> {

	@EmbeddedId
	private Key id;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ProjectRole role;

	@Column(name = "joined_at", nullable = false, updatable = false)
	private Instant joinedAt;

	@Transient
	private boolean isNew;

	protected ProjectMember() {
	}

	ProjectMember(UUID projectId, UUID userId, ProjectRole role, Instant now) {
		this.id = new Key(projectId, userId);
		this.role = role;
		this.joinedAt = now;
		this.isNew = true;
	}

	@Override
	public Key getId() {
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

	UUID getUserId() {
		return id.userId();
	}

	ProjectRole getRole() {
		return role;
	}

	Instant getJoinedAt() {
		return joinedAt;
	}

	void changeRole(ProjectRole role) {
		this.role = role;
	}

	/** Composite primary key (project, user). */
	@Embeddable
	record Key(@Column(name = "project_id") UUID projectId, @Column(name = "user_id") UUID userId)
			implements Serializable {
	}

}
