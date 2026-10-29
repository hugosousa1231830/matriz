package com.matriz.shared;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps every exception to the uniform problem format (ADR-0010): expected
 * errors keep their {@link ProblemCode}; Spring MVC errors get a code derived
 * from their status; anything else becomes a generic {@code 500} without
 * internal details.
 */
@RestControllerAdvice
@Order(Ordered.LOWEST_PRECEDENCE)
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

	@ExceptionHandler(ApiException.class)
	ResponseEntity<ProblemDetail> handleApiException(ApiException ex) {
		ProblemDetail body = ex.errors().isEmpty() ? Problems.of(ex.code(), ex.detail())
				: Problems.validation(ex.errors());
		return ResponseEntity.status(ex.code().status()).headers(ex.headers()).body(body);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ProblemDetail> handleUnexpected(Exception ex) {
		log.error("Unexpected error", ex);
		ProblemDetail body = Problems.of(ProblemCode.INTERNAL_ERROR);
		return ResponseEntity.status(body.getStatus()).body(body);
	}

	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
			HttpHeaders headers, HttpStatusCode status, WebRequest request) {
		List<Problems.FieldError> errors = ex.getBindingResult()
			.getFieldErrors()
			.stream()
			.map(e -> new Problems.FieldError(e.getField(),
					e.getDefaultMessage() != null ? e.getDefaultMessage() : "Invalid value."))
			.toList();
		return ResponseEntity.badRequest().body(Problems.validation(errors));
	}

	/** Spring MVC errors (malformed body, unknown path, wrong method, …) in the uniform format. */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		ProblemCode code = switch (statusCode.value()) {
			case 404 -> ProblemCode.NOT_FOUND;
			case 401 -> ProblemCode.UNAUTHENTICATED;
			case 403 -> ProblemCode.FORBIDDEN;
			default -> statusCode.is4xxClientError() ? ProblemCode.VALIDATION_ERROR : ProblemCode.INTERNAL_ERROR;
		};
		ProblemDetail problem = Problems.of(code);
		problem.setStatus(statusCode.value());
		if (statusCode.is5xxServerError()) {
			log.error("Request failed", ex);
		}
		return ResponseEntity.status(statusCode).headers(headers).body(problem);
	}

}
