package com.matriz.shared;

import java.util.List;

import org.springframework.http.HttpHeaders;

/**
 * An expected API error, rendered as {@code application/problem+json} with
 * the given {@link ProblemCode}. Messages must be generic and never contain
 * sensitive data.
 */
public class ApiException extends RuntimeException {

	private final ProblemCode code;

	private final transient HttpHeaders headers;

	private final transient List<Problems.FieldError> errors;

	public ApiException(ProblemCode code) {
		this(code, null, HttpHeaders.EMPTY);
	}

	public ApiException(ProblemCode code, String detail) {
		this(code, detail, HttpHeaders.EMPTY);
	}

	public ApiException(ProblemCode code, String detail, HttpHeaders headers) {
		this(code, detail, headers, List.of());
	}

	private ApiException(ProblemCode code, String detail, HttpHeaders headers, List<Problems.FieldError> errors) {
		super(detail != null ? detail : code.title(), null, false, false);
		this.code = code;
		this.headers = headers;
		this.errors = List.copyOf(errors);
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

	/** Field-level errors ({@code VALIDATION_ERROR} only). */
	public List<Problems.FieldError> errors() {
		return errors;
	}

	/** {@code 400 VALIDATION_ERROR} with field-level errors, for checks Bean Validation cannot express. */
	public static ApiException validation(List<Problems.FieldError> errors) {
		return new ApiException(ProblemCode.VALIDATION_ERROR, null, HttpHeaders.EMPTY, errors);
	}

	public static ApiException validation(String field, String message) {
		return validation(List.of(new Problems.FieldError(field, message)));
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
