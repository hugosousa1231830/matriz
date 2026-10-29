package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

import jakarta.servlet.http.Cookie;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.mockmvc.OpenApiValidationMatchers;
import com.atlassian.oai.validator.report.LevelResolver;
import com.atlassian.oai.validator.report.ValidationReport;

/**
 * Base class for HTTP-level integration tests: full application, PostgreSQL in
 * Testcontainers, fictitious demo data, and helpers that go through the real
 * CSRF and sign-in flow (cookies included, no security shortcuts).
 */
@SpringBootTest(properties = { "matriz.demo.seed=true", "matriz.demo.password=" + ApiIntegrationTest.DEMO_PASSWORD })
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
public abstract class ApiIntegrationTest {

	/** Fictitious password used only by the test database. */
	public static final String DEMO_PASSWORD = "Test-only-demo#1";

	private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

	@Autowired
	protected MockMvc mvc;

	@Autowired
	protected JdbcClient jdbc;

	// The fictitious demo domain (.test, RFC 2606) is not a public TLD, which the
	// validator's email format check rejects; the API's own validation still applies.
	// The validator also fails to parse comma-separated array query parameters whose
	// items are a $ref (e.g. ?status=TODO,DONE); TaskApiTests covers those values.
	private static final OpenApiInteractionValidator CONTRACT_VALIDATOR = OpenApiInteractionValidator
		.createForSpecificationUrl(ContractTests.CONTRACT.toUri().toString())
		.withLevelResolver(LevelResolver.create()
			.withLevel("validation.request.body.schema.format.email", ValidationReport.Level.IGNORE)
			.withLevel("validation.response.body.schema.format.email", ValidationReport.Level.IGNORE)
			.withLevel("validation.request.parameter.schema.invalidJson", ValidationReport.Level.IGNORE)
			.build())
		.build();

	/** Result matcher: the request and the response conform to openapi.yaml (ADR-0016). */
	protected static ResultMatcher matchesContract() {
		return OpenApiValidationMatchers.openApi().isValid(CONTRACT_VALIDATOR);
	}

	/** A distinct client IP per call, so the per-IP sign-in limit does not leak between tests. */
	protected static RequestPostProcessor freshIp() {
		return fromIp(nextIp());
	}

	protected static String nextIp() {
		int n = NEXT_IP.getAndIncrement();
		return "10.0." + (n / 250) + "." + (n % 250 + 1);
	}

	protected static RequestPostProcessor fromIp(String ip) {
		return (MockHttpServletRequest request) -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

	/** Calls {@code GET /auth/csrf} and returns the issued XSRF-TOKEN cookie. */
	protected Cookie csrfCookie() throws Exception {
		Cookie cookie = mvc.perform(get("/api/v1/auth/csrf")).andReturn().getResponse().getCookie("XSRF-TOKEN");
		assertThat(cookie).isNotNull();
		return cookie;
	}

	protected static MockHttpServletRequestBuilder signInRequest(Cookie xsrf, String email, String password) {
		return post("/api/v1/auth/sign-in").cookie(xsrf)
			.header("X-XSRF-TOKEN", xsrf.getValue())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"" + email + "\",\"password\":\"" + password + "\"}");
	}

	/** Signs in with the demo password and returns the resulting browser state. */
	protected Session signIn(String email) throws Exception {
		MvcResult result = mvc.perform(signInRequest(csrfCookie(), email, DEMO_PASSWORD).with(freshIp()))
			.andReturn();
		assertThat(result.getResponse().getStatus()).as("sign-in of %s", email).isEqualTo(200);
		Cookie session = result.getResponse().getCookie("matriz_session");
		Cookie xsrf = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(session).isNotNull();
		assertThat(xsrf).isNotNull();
		return new Session(session, xsrf);
	}

	/** Cookies held by a signed-in browser. */
	public record Session(Cookie sessionCookie, Cookie xsrfCookie) {

		/** Adds the session cookie, plus the CSRF cookie and header. */
		public RequestPostProcessor auth() {
			return request -> {
				request.setCookies(sessionCookie, xsrfCookie);
				request.addHeader("X-XSRF-TOKEN", xsrfCookie.getValue());
				return request;
			};
		}

		/** Session id as stored in {@code spring_session.session_id} (the cookie is Base64-encoded). */
		public String sessionId() {
			return new String(Base64.getDecoder().decode(sessionCookie.getValue()), StandardCharsets.UTF_8);
		}

	}

}
