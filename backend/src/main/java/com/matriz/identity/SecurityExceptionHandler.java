package com.matriz.identity;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.csrf.CsrfException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.matriz.shared.ProblemCode;
import com.matriz.shared.Problems;

/**
 * Problem JSON for security errors, raised in controllers or delegated from the
 * security filter chain: no/expired session → {@code 401 UNAUTHENTICATED};
 * missing/invalid CSRF token → {@code 403 CSRF_INVALID}; otherwise
 * {@code 403 FORBIDDEN}.
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
class SecurityExceptionHandler {

	@ExceptionHandler(AuthenticationException.class)
	ResponseEntity<ProblemDetail> unauthenticated() {
		return problem(ProblemCode.UNAUTHENTICATED);
	}

	@ExceptionHandler(CsrfException.class)
	ResponseEntity<ProblemDetail> csrfInvalid() {
		return problem(ProblemCode.CSRF_INVALID);
	}

	@ExceptionHandler(AccessDeniedException.class)
	ResponseEntity<ProblemDetail> forbidden() {
		return problem(ProblemCode.FORBIDDEN);
	}

	private static ResponseEntity<ProblemDetail> problem(ProblemCode code) {
		return ResponseEntity.status(code.status()).body(Problems.of(code));
	}

}
