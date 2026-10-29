package com.matriz.shared;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes of the API ({@code Problem.code} in
 * {@code openapi.yaml}), with their HTTP status and generic title.
 */
public enum ProblemCode {

	VALIDATION_ERROR(HttpStatus.BAD_REQUEST, "Invalid request"),
	UNAUTHENTICATED(HttpStatus.UNAUTHORIZED, "Authentication required"),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "Invalid email or password"),
	TOO_MANY_ATTEMPTS(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts"),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "Invalid or missing CSRF token"),
	FORBIDDEN(HttpStatus.FORBIDDEN, "Not allowed"),
	NOT_FOUND(HttpStatus.NOT_FOUND, "Not found"),
	VERSION_CONFLICT(HttpStatus.CONFLICT, "Task was modified"),
	PROJECT_ARCHIVED(HttpStatus.CONFLICT, "Project is archived"),
	ALREADY_MEMBER(HttpStatus.CONFLICT, "User is already a member"),
	ALREADY_ASSIGNED(HttpStatus.CONFLICT, "User is already assigned"),
	USER_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT, "User cannot be added"),
	ASSIGNEE_NOT_ELIGIBLE(HttpStatus.UNPROCESSABLE_CONTENT, "User cannot be assigned"),
	OWNER_IMMUTABLE(HttpStatus.UNPROCESSABLE_CONTENT, "The project owner cannot be removed or demoted"),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error");

	private final HttpStatus status;

	private final String title;

	ProblemCode(HttpStatus status, String title) {
		this.status = status;
		this.title = title;
	}

	public HttpStatus status() {
		return status;
	}

	public String title() {
		return title;
	}

	/** Relative problem type, e.g. {@code /problems/project-archived}. */
	public String type() {
		return "/problems/" + name().toLowerCase().replace('_', '-');
	}

}
