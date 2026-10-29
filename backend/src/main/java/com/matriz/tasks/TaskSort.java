package com.matriz.tasks;

import java.util.Arrays;
import java.util.Optional;

/**
 * Sort options of the task list ({@code sort} in openapi.yaml). Enums sort in
 * their semantic order; ties are broken by {@code createdAt} descending, then id.
 */
enum TaskSort {

	CREATED_AT("createdAt", "t.created_at"),
	PRIORITY("priority", "CASE t.priority WHEN 'LOW' THEN 1 WHEN 'MEDIUM' THEN 2 ELSE 3 END"),
	STATUS("status", "CASE t.status WHEN 'TODO' THEN 1 WHEN 'IN_PROGRESS' THEN 2 ELSE 3 END"),
	TITLE("title", "lower(t.title)");

	private final String field;

	private final String expression;

	TaskSort(String field, String expression) {
		this.field = field;
		this.expression = expression;
	}

	/** A sort key with direction, e.g. {@code -createdAt} (the default). */
	record Order(TaskSort sort, boolean descending) {

		static final Order DEFAULT = new Order(CREATED_AT, true);

		/** Parses {@code field} or {@code -field}; empty when unknown. */
		static Optional<Order> parse(String value) {
			boolean descending = value.startsWith("-");
			String field = descending ? value.substring(1) : value;
			return Arrays.stream(values()).filter(s -> s.field.equals(field)).findFirst()
				.map(s -> new Order(s, descending));
		}

		/** SQL {@code ORDER BY} list (built only from the constants above). */
		String orderBy() {
			String direction = descending ? " DESC" : " ASC";
			String tieBreak = sort == CREATED_AT ? "" : ", t.created_at DESC";
			return sort.expression + direction + tieBreak + ", t.id";
		}

	}

}
