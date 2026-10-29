package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.SimpleRequest;
import com.atlassian.oai.validator.report.ValidationReport;

/**
 * The OpenAPI 3.1 contract can be loaded by the validator used for backend
 * contract tests (ADR-0016).
 */
public class ContractTests {

	public static final Path CONTRACT = Path.of("..", "openapi.yaml").toAbsolutePath().normalize();

	@Test
	void contractLoadsAndValidatesKnownAndUnknownOperations() {
		OpenApiInteractionValidator validator = OpenApiInteractionValidator
			.createForSpecificationUrl(CONTRACT.toUri().toString())
			.build();

		ValidationReport known = validator.validateRequest(SimpleRequest.Builder.get("/api/v1/auth/me").build());
		ValidationReport unknown = validator
			.validateRequest(SimpleRequest.Builder.get("/api/v1/does-not-exist").build());

		assertThat(known.getMessages()).noneMatch(m -> m.getKey().equals("validation.request.path.missing"));
		assertThat(unknown.getMessages()).anyMatch(m -> m.getKey().equals("validation.request.path.missing"));
	}

}
