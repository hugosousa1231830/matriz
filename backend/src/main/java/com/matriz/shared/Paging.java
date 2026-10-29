package com.matriz.shared;

import java.util.ArrayList;
import java.util.List;

/**
 * Validated {@code page} (1-based) and {@code pageSize} query parameters
 * (contract defaults 1 and 20, at most 100 items per page).
 */
public record Paging(int page, int pageSize) {

	public static final int DEFAULT_PAGE_SIZE = 20;

	public static final int MAX_PAGE_SIZE = 100;

	/** {@code 400 VALIDATION_ERROR} when a value is out of range; {@code null} means the default. */
	public static Paging of(Integer page, Integer pageSize) {
		List<Problems.FieldError> errors = new ArrayList<>();
		if (page != null && page < 1) {
			errors.add(new Problems.FieldError("page", "Page must be 1 or greater."));
		}
		if (pageSize != null && (pageSize < 1 || pageSize > MAX_PAGE_SIZE)) {
			errors.add(new Problems.FieldError("pageSize", "Page size must be between 1 and " + MAX_PAGE_SIZE + "."));
		}
		if (!errors.isEmpty()) {
			throw ApiException.validation(errors);
		}
		return new Paging(page == null ? 1 : page, pageSize == null ? DEFAULT_PAGE_SIZE : pageSize);
	}

	public long offset() {
		return (long) (page - 1) * pageSize;
	}

}
