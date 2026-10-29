package com.matriz.shared;

import org.springframework.http.HttpHeaders;

/**
 * An expected API error, rendered as {@code application/problem+json} with
 * the given {@link ProblemCode}. Messages must be generic and never contain
 * sensitive data.
 */
public class ApiException extends RuntimeException {

	private final ProblemCode code;

	private final transient HttpHeaders headers;

	public ApiException(ProblemCode code) {
		this(code, null, HttpHeaders.EMPTY);
	}

	public ApiException(ProblemCode code, String detail) {
		this(code, detail, HttpHeaders.EMPTY);
	}

	public ApiException(ProblemCode code, String detail, HttpHeaders headers) {
		super(detail != null ? detail : code.title(), null, false, false);
		this.code = code;
		this.headers = headers;
	}

	public ProblemCode code() {
		return code;
	}

	/** Optional detail sent to the client ({@code null} when only the title applies). */
	public String detail() {
		String message = getMessage();
		return code.title().equals(message) ? null : message;
	}

	public HttpHeaders headers() {
		return headers;
	}

	public static ApiException notFound() {
		return new ApiException(ProblemCode.NOT_FOUND);
	}

	public static ApiException forbidden() {
		return new ApiException(ProblemCode.FORBIDDEN);
	}

	public static ApiException projectArchived() {
		return new ApiException(ProblemCode.PROJECT_ARCHIVED, "Archived projects are read-only.");
	}

}
