package com.matriz.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import com.matriz.ApiIntegrationTest;

/** Session authentication end to end: CSRF, sign-in, profile, sign-out, expiry and lockout. */
class AuthenticationTests extends ApiIntegrationTest {

	private static final String ANA = "ana@matriz.test";

	private static final String MARTA = "marta@matriz.test";

	private static final String VITOR = "vitor@matriz.test";

	private static final String JOAO = "joao@matriz.test";

	@AfterEach
	void resetLockout() {
		jdbc.sql("UPDATE users SET failed_login_attempts = 0, locked_until = NULL").update();
	}

	@Test
	void csrfEndpointIssuesReadableTokenCookie() throws Exception {
		mvc.perform(get("/api/v1/auth/csrf"))
			.andExpect(status().isNoContent())
			.andExpect(cookie().exists("XSRF-TOKEN"))
			.andExpect(cookie().httpOnly("XSRF-TOKEN", false))
			.andExpect(matchesContract());
	}

	@Test
	void validCredentialsStartSessionAndReturnProfile() throws Exception {
		Cookie xsrf = csrfCookie();

		MvcResult result = mvc.perform(signInRequest(xsrf, ANA, DEMO_PASSWORD).with(freshIp()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ANA))
			.andExpect(jsonPath("$.name").value("Ana Costa"))
			.andExpect(jsonPath("$.isAdmin").value(false))
			.andExpect(jsonPath("$.password").doesNotExist())
			.andExpect(cookie().exists("matriz_session"))
			.andExpect(cookie().httpOnly("matriz_session", true))
			.andExpect(matchesContract())
			.andReturn();

		Cookie rotated = result.getResponse().getCookie("XSRF-TOKEN");
		assertThat(rotated).isNotNull();
		assertThat(rotated.getValue()).as("CSRF token rotates at sign-in").isNotEqualTo(xsrf.getValue());
	}

	@Test
	void emailIsMatchedCaseInsensitively() throws Exception {
		mvc.perform(signInRequest(csrfCookie(), "Ana@Matriz.TEST", DEMO_PASSWORD).with(freshIp()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ANA));
	}

	@Test
	void invalidCredentialsGetTheSameGeneric401() throws Exception {
		String wrongPassword = mvc.perform(signInRequest(csrfCookie(), ANA, "not-the-password").with(freshIp()))
			.andExpect(status().isUnauthorized())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
			.andExpect(cookie().doesNotExist("matriz_session"))
			.andExpect(matchesContract())
			.andReturn()
			.getResponse()
			.getContentAsString();

		String unknownEmail = mvc
			.perform(signInRequest(csrfCookie(), "nobody@matriz.test", DEMO_PASSWORD).with(freshIp()))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		jdbc.sql("UPDATE users SET locked_until = now() + interval '10 minutes' WHERE email = :e")
			.param("e", MARTA)
			.update();
		String locked = mvc.perform(signInRequest(csrfCookie(), MARTA, DEMO_PASSWORD).with(freshIp()))
			.andExpect(status().isUnauthorized())
			.andReturn()
			.getResponse()
			.getContentAsString();

		assertThat(unknownEmail).isEqualTo(wrongPassword);
		assertThat(locked).isEqualTo(wrongPassword);
		assertThat(wrongPassword).doesNotContainIgnoringCase("lock").doesNotContainIgnoringCase("inactive");
	}

	@Test
	void malformedSignInIsRejectedWithFieldErrors() throws Exception {
		Cookie xsrf = csrfCookie();
		mvc.perform(post("/api/v1/auth/sign-in").cookie(xsrf)
			.header("X-XSRF-TOKEN", xsrf.getValue())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"not-an-email\",\"password\":\"\"}")
			.with(freshIp()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[?(@.field == 'email')]").exists())
			.andExpect(jsonPath("$.errors[?(@.field == 'password')]").exists());

		mvc.perform(post("/api/v1/auth/sign-in").cookie(xsrf)
			.header("X-XSRF-TOKEN", xsrf.getValue())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"ana@matriz.test\",\"password\":\"x\",\"isAdmin\":true}")
			.with(freshIp()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
	}

	@Test
	void requestsWithoutSessionAreUnauthenticated() throws Exception {
		mvc.perform(get("/api/v1/auth/me"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(get("/api/v1/projects"))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		mvc.perform(get("/api/v1/auth/me").cookie(new Cookie("matriz_session", "Zm9yZ2Vk")))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void profileReflectsTheSignedInUser() throws Exception {
		Session ana = signIn(ANA);
		Session vitor = signIn(VITOR);

		mvc.perform(get("/api/v1/auth/me").with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(ANA))
			.andExpect(jsonPath("$.isAdmin").value(false))
			.andExpect(matchesContract());
		mvc.perform(get("/api/v1/auth/me").with(vitor.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.email").value(VITOR))
			.andExpect(jsonPath("$.isAdmin").value(true));
	}

	@Test
	void sessionIsStoredInDatabaseWithIdleTimeoutAndUserIdAsPrincipal() throws Exception {
		Session ana = signIn(ANA);

		var row = jdbc
			.sql("SELECT principal_name, max_inactive_interval FROM spring_session WHERE session_id = :id")
			.param("id", ana.sessionId())
			.query((rs, n) -> new Object[] { rs.getString(1), rs.getInt(2) })
			.single();
		String anaId = jdbc.sql("SELECT id::text FROM users WHERE email = :e").param("e", ANA).query(String.class).single();

		assertThat(row[0]).isEqualTo(anaId);
		assertThat(row[1]).isEqualTo((int) Duration.ofMinutes(30).toSeconds());
	}

	@Test
	void signInReplacesAnExistingSession() throws Exception {
		Session first = signIn(ANA);
		MvcResult again = mvc
			.perform(signInRequest(first.xsrfCookie(), MARTA, DEMO_PASSWORD).cookie(first.sessionCookie()).with(freshIp()))
			.andExpect(status().isOk())
			.andReturn();

		Cookie second = again.getResponse().getCookie("matriz_session");
		assertThat(second).isNotNull();
		assertThat(second.getValue()).isNotEqualTo(first.sessionCookie().getValue());
		mvc.perform(get("/api/v1/auth/me").with(first.auth())).andExpect(status().isUnauthorized());
	}

	@Test
	void signOutInvalidatesTheSession() throws Exception {
		Session ana = signIn(ANA);

		mvc.perform(post("/api/v1/auth/sign-out").with(ana.auth()))
			.andExpect(status().isNoContent())
			.andExpect(cookie().maxAge("XSRF-TOKEN", 0))
			.andExpect(matchesContract());

		mvc.perform(get("/api/v1/auth/me").with(ana.auth())).andExpect(status().isUnauthorized());
		assertThat(jdbc.sql("SELECT count(*) FROM spring_session WHERE session_id = :id")
			.param("id", ana.sessionId())
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void signOutWithoutSessionIsUnauthenticated() throws Exception {
		Cookie xsrf = csrfCookie();
		mvc.perform(post("/api/v1/auth/sign-out").cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue()))
			.andExpect(status().isUnauthorized());
	}

	@Test
	void stateChangingRequestsWithoutCsrfTokenAreRejected() throws Exception {
		Session ana = signIn(ANA);

		mvc.perform(post("/api/v1/auth/sign-out").cookie(ana.sessionCookie(), ana.xsrfCookie()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mvc.perform(post("/api/v1/auth/sign-out").cookie(ana.sessionCookie(), ana.xsrfCookie())
			.header("X-XSRF-TOKEN", "wrong-token"))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mvc.perform(post("/api/v1/auth/sign-in").contentType(MediaType.APPLICATION_JSON)
			.content("{\"email\":\"ana@matriz.test\",\"password\":\"x\"}")
			.with(freshIp()))
			.andExpect(status().isForbidden())
			.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		mvc.perform(get("/api/v1/auth/me").with(ana.auth())).andExpect(status().isOk());
	}

	@Test
	void idleSessionExpiresAfter30Minutes() throws Exception {
		Session ana = signIn(ANA);
		jdbc.sql("UPDATE spring_session SET last_access_time = last_access_time - :ms WHERE session_id = :id")
			.param("ms", Duration.ofMinutes(31).toMillis())
			.param("id", ana.sessionId())
			.update();

		mvc.perform(get("/api/v1/auth/me").with(ana.auth()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
	}

	@Test
	void sessionExpiresEightHoursAfterSignInEvenWhenActive() throws Exception {
		Session ana = signIn(ANA);
		jdbc.sql("UPDATE spring_session SET creation_time = creation_time - :ms WHERE session_id = :id")
			.param("ms", Duration.ofHours(8).plusMinutes(1).toMillis())
			.param("id", ana.sessionId())
			.update();

		mvc.perform(get("/api/v1/auth/me").with(ana.auth())).andExpect(status().isUnauthorized());
		assertThat(jdbc.sql("SELECT count(*) FROM spring_session WHERE session_id = :id")
			.param("id", ana.sessionId())
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void deactivatedUserLosesAccessOnNextRequest() throws Exception {
		Session joao = signIn(JOAO);
		jdbc.sql("UPDATE users SET is_active = false WHERE email = :e").param("e", JOAO).update();
		try {
			mvc.perform(get("/api/v1/auth/me").with(joao.auth())).andExpect(status().isUnauthorized());
		}
		finally {
			jdbc.sql("UPDATE users SET is_active = true WHERE email = :e").param("e", JOAO).update();
		}
	}

	@Test
	void fiveFailuresLockTheAccountFor15Minutes() throws Exception {
		for (int i = 0; i < 5; i++) {
			mvc.perform(signInRequest(csrfCookie(), JOAO, "wrong-" + i).with(freshIp()))
				.andExpect(status().isUnauthorized());
		}
		Double lockedFor = jdbc
			.sql("SELECT extract(epoch FROM locked_until - now())::float8 FROM users WHERE email = :e")
			.param("e", JOAO)
			.query(Double.class)
			.single();
		assertThat(lockedFor).isBetween(14 * 60.0, 15 * 60.0 + 5);

		mvc.perform(signInRequest(csrfCookie(), JOAO, DEMO_PASSWORD).with(freshIp()))
			.andExpect(status().isUnauthorized())
			.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

		jdbc.sql("UPDATE users SET locked_until = now() - interval '1 second' WHERE email = :e")
			.param("e", JOAO)
			.update();
		mvc.perform(signInRequest(csrfCookie(), JOAO, DEMO_PASSWORD).with(freshIp())).andExpect(status().isOk());
		assertThat(jdbc.sql("SELECT failed_login_attempts FROM users WHERE email = :e")
			.param("e", JOAO)
			.query(Integer.class)
			.single()).isZero();
	}

	@Test
	void successfulSignInResetsTheFailureCount() throws Exception {
		for (int i = 0; i < 4; i++) {
			mvc.perform(signInRequest(csrfCookie(), JOAO, "wrong-" + i).with(freshIp()));
		}
		signIn(JOAO);
		mvc.perform(signInRequest(csrfCookie(), JOAO, "wrong-again").with(freshIp()));

		assertThat(jdbc.sql("SELECT locked_until IS NULL FROM users WHERE email = :e")
			.param("e", JOAO)
			.query(Boolean.class)
			.single()).isTrue();
	}

	@Test
	void moreThan20AttemptsPerMinuteFromOneIpAreThrottled() throws Exception {
		String ip = nextIp();
		Cookie xsrf = csrfCookie();
		for (int i = 0; i < 20; i++) {
			mvc.perform(signInRequest(xsrf, "nobody" + i + "@matriz.test", "x").with(fromIp(ip)))
				.andExpect(status().isUnauthorized());
		}

		mvc.perform(signInRequest(xsrf, ANA, DEMO_PASSWORD).with(fromIp(ip)))
			.andExpect(status().isTooManyRequests())
			.andExpect(header().exists("Retry-After"))
			.andExpect(jsonPath("$.code").value("TOO_MANY_ATTEMPTS"))
			.andExpect(matchesContract());

		mvc.perform(signInRequest(xsrf, ANA, DEMO_PASSWORD).with(freshIp())).andExpect(status().isOk());
	}

	@Test
	void passwordsAreStoredAsArgon2idHashesOnly() {
		var hashes = jdbc.sql("SELECT password_hash FROM users").query(String.class).list();

		assertThat(hashes).isNotEmpty().allMatch(hash -> hash.startsWith("$argon2id$"));
		assertThat(hashes).noneMatch(hash -> hash.contains(DEMO_PASSWORD));
	}

}
