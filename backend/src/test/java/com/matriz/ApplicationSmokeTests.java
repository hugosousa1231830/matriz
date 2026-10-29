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

/** The application starts, reports database health and keeps the API closed. */
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
	void apiIsNotAccessibleBeforeAuthenticationExists() throws Exception {
		assertThat(get("/api/v1/projects").statusCode()).isIn(401, 403);
	}

	private HttpResponse<String> get(String path) throws Exception {
		String port = environment.getRequiredProperty("local.server.port");
		HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
		return http.send(request, HttpResponse.BodyHandlers.ofString());
	}

}
