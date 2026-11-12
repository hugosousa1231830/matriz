package com.matriz.shared;

import java.util.Locale;

/** Text input helpers: trimming, length and safe {@code LIKE} patterns. */
public final class Text {

	private Text() {
	}

	/**
	 * Leading and trailing whitespace removed; {@code null} stays {@code null}.
	 * Whitespace covers everything ECMAScript {@code trim()} and the contract's
	 * {@code pattern: '\\S'} treat as such: {@link Character#isWhitespace},
	 * every Unicode space separator ({@link Character#isSpaceChar}, e.g. U+00A0,
	 * U+2007, U+202F) and U+FEFF. {@link String#strip()} alone keeps U+00A0,
	 * U+2007, U+202F and U+FEFF (finding F-01).
	 */
	public static String trim(String value) {
		if (value == null) {
			return null;
		}
		int start = 0;
		int end = value.length();
		while (start < end) {
			int cp = value.codePointAt(start);
			if (!isWhitespace(cp)) {
				break;
			}
			start += Character.charCount(cp);
		}
		while (end > start) {
			int cp = value.codePointBefore(end);
			if (!isWhitespace(cp)) {
				break;
			}
			end -= Character.charCount(cp);
		}
		return value.substring(start, end);
	}

	private static boolean isWhitespace(int codePoint) {
		return Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint) || codePoint == '\uFEFF';
	}

	/** Trimmed value, or {@code null} when absent or empty after trimming (optional text fields). */
	public static String trimToNull(String value) {
		String trimmed = trim(value);
		return trimmed == null || trimmed.isEmpty() ? null : trimmed;
	}

	/**
	 * Number of characters as Unicode code points, the way PostgreSQL's
	 * {@code char_length} and JSON Schema {@code minLength}/{@code maxLength}
	 * count them ({@code String.length()} counts an emoji as two).
	 */
	public static int length(String value) {
		return value.codePointCount(0, value.length());
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
