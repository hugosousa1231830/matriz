package com.matriz.shared;

import java.net.URI;
import java.util.List;
import java.util.Map;

import org.springframework.http.ProblemDetail;

/** Builds RFC 9457 problem details in the API's uniform format (ADR-0010). */
public final class Problems {

	private Problems() {
	}

	public static ProblemDetail of(ProblemCode code) {
		return of(code, null);
	}

	/** @param detail optional generic explanation; must never contain sensitive data */
	public static ProblemDetail of(ProblemCode code, String detail) {
		ProblemDetail problem = ProblemDetail.forStatus(code.status());
		problem.setType(URI.create(code.type()));
		problem.setTitle(code.title());
		if (detail != null) {
			problem.setDetail(detail);
		}
		problem.setProperty("code", code.name());
		return problem;
	}

	/** {@code 400 VALIDATION_ERROR} with field-level errors. */
	public static ProblemDetail validation(List<FieldError> errors) {
		ProblemDetail problem = of(ProblemCode.VALIDATION_ERROR);
		problem.setProperty("errors",
				errors.stream().map(e -> Map.of("field", e.field(), "message", e.message())).toList());
		return problem;
	}

	/** A field-level validation error. */
	public record FieldError(String field, String message) {
	}

}
