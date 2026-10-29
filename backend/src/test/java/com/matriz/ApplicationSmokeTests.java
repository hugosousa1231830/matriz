package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;

/** The application starts, reports database health and requires a session for the API. */
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ApplicationSmokeTests {

	@Autowired
	Environment environment;

	private final HttpClient http = HttpClient.newHttpClient();

	@Test
	void healthEndpointReportsDatabaseUp() throws Exception {
		HttpResponse<String> response = get("/actuator/health");

		assertThat(response.statusCode()).isEqualTo(200);
		assertThat(response.body()).contains("\"status\":\"UP\"").contains("\"db\":{\"status\":\"UP\"}");
	}

	@Test
	void apiRequiresAuthentication() throws Exception {
		HttpResponse<String> response = get("/api/v1/auth/me");

		assertThat(response.statusCode()).isEqualTo(401);
		assertThat(response.headers().firstValue("Content-Type")).hasValueSatisfying(
				type -> assertThat(type).startsWith("application/problem+json"));
		assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
	}

	@Test
	void unknownNonApiPathsAreDenied() throws Exception {
		assertThat(get("/actuator/env").statusCode()).isIn(401, 403, 404);
	}

	private HttpResponse<String> get(String path) throws Exception {
		String port = environment.getRequiredProperty("local.server.port");
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
