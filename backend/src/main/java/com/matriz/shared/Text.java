package com.matriz.shared;

import java.util.Locale;

/** Text input helpers: trimming and safe {@code LIKE} patterns. */
public final class Text {

	private Text() {
	}

	/** Leading and trailing whitespace removed; {@code null} stays {@code null}. */
	public static String trim(String value) {
		return value == null ? null : value.strip();
	}

	/** Trimmed value, or {@code null} when absent or empty after trimming (optional text fields). */
	public static String trimToNull(String value) {
		String trimmed = trim(value);
		return trimmed == null || trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * Case-insensitive "contains" pattern for {@code lower(column) LIKE :pattern ESCAPE '\'}:
	 * the value is lower-cased and its {@code %}, {@code _} and {@code \} are escaped.
	 */
	public static String containsPattern(String value) {
		String escaped = value.toLowerCase(Locale.ROOT).replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
		return "%" + escaped + "%";
	}

}
