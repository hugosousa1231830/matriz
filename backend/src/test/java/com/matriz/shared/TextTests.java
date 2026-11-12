package com.matriz.shared;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Unit tests of {@link Text#trim} and {@link Text#trimToNull}: Unicode space
 * separators and U+FEFF are whitespace, as in ECMAScript {@code trim()} and
 * the contract's {@code pattern: '\\S'} (finding F-01 in
 * docs/quality-security-review.md).
 */
class TextTests {

	@ParameterizedTest
	@ValueSource(strings = { "\u00A0", "\u2007", "\u202F", "\uFEFF", "\u3000", "\u2028", "\u2029", " ", "\t", "\n" })
	void unicodeWhitespaceIsTrimmed(String space) {
		assertThat(Text.trim(space + space + "abc" + space)).isEqualTo("abc");
		assertThat(Text.trim(space.repeat(3))).isEmpty();
		assertThat(Text.trimToNull(space.repeat(3))).isNull();
	}

	@Test
	void innerWhitespaceAndOtherCharactersAreKept() {
		assertThat(Text.trim(" \u00A0a\u00A0b c\u200B ")).isEqualTo("a\u00A0b c\u200B");
		assertThat(Text.trim("\uD83D\uDE00")).isEqualTo("\uD83D\uDE00");
		assertThat(Text.trim("")).isEmpty();
		assertThat(Text.trim(null)).isNull();
		assertThat(Text.trimToNull(null)).isNull();
	}

}
