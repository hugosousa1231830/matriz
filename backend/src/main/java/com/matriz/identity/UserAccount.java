package com.matriz.identity;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A local user account ({@code users} table). Accounts are seeded only in release 1. */
@Entity
@Table(name = "users")
class UserAccount {

	@Id
	private UUID id;

	@Column(nullable = false, length = 254)
	private String email;

	@Column(nullable = false, length = 100)
	private String name;

	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	@Column(name = "is_admin", nullable = false)
	private boolean admin;

	@Column(name = "is_active", nullable = false)
	private boolean active;

	@Column(name = "failed_login_attempts", nullable = false)
	private int failedLoginAttempts;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	protected UserAccount() {
	}

	UUID getId() {
		return id;
	}

	String getEmail() {
		return email;
	}

	String getName() {
		return name;
	}

	String getPasswordHash() {
		return passwordHash;
	}

	boolean isAdmin() {
		return admin;
	}

	boolean isActive() {
		return active;
	}

	int getFailedLoginAttempts() {
		return failedLoginAttempts;
	}

	boolean isLockedAt(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	AuthenticatedUser toAuthenticatedUser() {
		return new AuthenticatedUser(id, email, name, admin);
	}

}
