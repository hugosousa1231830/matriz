package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.matriz.shared.ApiException;
import com.matriz.shared.ProblemCode;
import com.matriz.shared.Problems;

/**
 * Unit tests of the task title rule (requirements.md validation limits;
 * {@code TaskCreateRequest.title} in openapi.yaml): trimmed, 3–150
 * characters counted as code points, whitespace-only rejected. No Spring
 * context and no database: test-plan.md TV-01 to TV-06.
 */
class TaskTitleRuleTests {

	/** Three U+00A0: whitespace for openapi.yaml ({@code pattern: '\\S'}, ECMA-262) and the UI. */
	private static final String NO_BREAK_SPACES = "\u00A0\u00A0\u00A0";

	private static ValidatorFactory factory;

	private static Validator validator;

	@BeforeAll
	static void createValidator() {
		factory = Validation.buildDefaultValidatorFactory();
		validator = factory.getValidator();
	}

	@AfterAll
	static void closeValidator() {
		factory.close();
	}

	private static Set<ConstraintViolation<TaskCreateRequest>> titleViolations(String title) {
		return validator.validateProperty(new TaskCreateRequest(title, null, null, null), "title");
	}

	private static void assertTitleRejected(String title, String message) {
		assertThat(titleViolations(title)).extracting(ConstraintViolation::getMessage).containsExactly(message);
	}

	/** Create: Bean Validation on {@link TaskCreateRequest}, after the record trims the title. */
	@Nested
	class Create {

		@Test
		void validTitleIsAcceptedAndTrimmed() {
			TaskCreateRequest request = new TaskCreateRequest("  Prepare release notes  ", null, null, null);

			assertThat(request.title()).isEqualTo("Prepare release notes");
			assertThat(validator.validate(request)).isEmpty();
		}

		@Test
		void missingTitleIsRequired() {
			assertTitleRejected(null, "Title is required.");
		}

		@ParameterizedTest
		@ValueSource(strings = { "", "   ", "\t\n " })
		void emptyOrWhitespaceOnlyTitleIsRejected(String title) {
			assertTitleRejected(title, TaskRules.TITLE_MESSAGE);
		}

		@ParameterizedTest
		@ValueSource(strings = { "ab", " ab ", "  ab\t" })
		void titleBelowMinimumAfterTrimIsRejected(String title) {
			assertTitleRejected(title, TaskRules.TITLE_MESSAGE);
		}

		@ParameterizedTest
		@ValueSource(strings = { "abc", "  abc  ", "\tabc\n" })
		void titleAtMinimumAfterTrimIsAccepted(String title) {
			TaskCreateRequest request = new TaskCreateRequest(title, null, null, null);

			assertThat(request.title()).isEqualTo("abc");
			assertThat(validator.validate(request)).isEmpty();
		}

		@Test
		void titleAtMaximumIsAccepted() {
			assertThat(titleViolations("a".repeat(150))).isEmpty();
		}

		@Test
		void titleAtMaximumWithSurroundingSpacesIsAccepted() {
			TaskCreateRequest request = new TaskCreateRequest("  " + "a".repeat(150) + "  ", null, null, null);

			assertThat(request.title()).hasSize(150);
			assertThat(validator.validate(request)).isEmpty();
		}

		@Test
		void titleAboveMaximumIsRejected() {
			assertTitleRejected("a".repeat(151), TaskRules.TITLE_MESSAGE);
		}

		@Disabled("Finding F-01 in docs/test-plan.md: the API trims with String.strip(), which keeps U+00A0")
		@Test
		void noBreakSpaceOnlyTitleIsRejected() {
			assertThat(titleViolations(NO_BREAK_SPACES)).isNotEmpty();
		}

		@Test
		void lengthIsCountedInCodePoints() {
			assertTitleRejected("😀😀", TaskRules.TITLE_MESSAGE);
			assertThat(titleViolations("😀😀😀")).isEmpty();
			assertThat(titleViolations("😀".repeat(150))).isEmpty();
			assertTitleRejected("😀".repeat(151), TaskRules.TITLE_MESSAGE);
		}

	}

	/** Edit: {@link TaskRules#validate} on a partial update with a title. */
	@Nested
	class Update {

		private static TaskUpdateRequest update(String title) {
			TaskUpdateRequest body = new TaskUpdateRequest();
			body.setTitle(title);
			body.setVersion(1);
			return body;
		}

		private static void assertTitleRejected(String title, String message) {
			assertThatThrownBy(() -> TaskRules.validate(update(title))).isInstanceOfSatisfying(ApiException.class,
					(ex) -> {
						assertThat(ex.code()).isEqualTo(ProblemCode.VALIDATION_ERROR);
						assertThat(ex.errors()).containsExactly(new Problems.FieldError("title", message));
					});
		}

		@Test
		void validTitleIsAcceptedAndTrimmed() {
			TaskService.TaskChanges changes = TaskRules.validate(update("  Prepare release notes  "));

			assertThat(changes.title()).isEqualTo("Prepare release notes");
		}

		@Test
		void nullTitleIsRequired() {
			assertTitleRejected(null, "Title is required.");
		}

		@ParameterizedTest
		@ValueSource(strings = { "", "   ", " ab ", "😀😀" })
		void titleBelowMinimumAfterTrimIsRejected(String title) {
			assertTitleRejected(title, TaskRules.TITLE_MESSAGE);
		}

		@ParameterizedTest
		@CsvSource(delimiter = '|', quoteCharacter = '"', value = { "abc|abc", "\"  abc  \"|abc", "😀😀😀|😀😀😀" })
		void titleAtMinimumAfterTrimIsAccepted(String title, String stored) {
			assertThat(TaskRules.validate(update(title)).title()).isEqualTo(stored);
		}

		@Disabled("Finding F-01 in docs/test-plan.md: the API trims with String.strip(), which keeps U+00A0")
		@Test
		void noBreakSpaceOnlyTitleIsRejected() {
			assertTitleRejected(NO_BREAK_SPACES, TaskRules.TITLE_MESSAGE);
		}

		@Test
		void titleAtMaximumIsAcceptedAndAboveIsRejected() {
			assertThat(TaskRules.validate(update("a".repeat(150))).title()).hasSize(150);
			assertTitleRejected("a".repeat(151), TaskRules.TITLE_MESSAGE);
		}

	}

}
