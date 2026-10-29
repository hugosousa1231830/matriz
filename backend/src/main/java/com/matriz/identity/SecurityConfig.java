package com.matriz.identity;

import java.time.Clock;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.session.web.http.CookieSerializer;
import org.springframework.session.web.http.DefaultCookieSerializer;
import org.springframework.web.servlet.HandlerExceptionResolver;

/**
 * Session-based authentication for the SPA (ADR-0009).
 * <ul>
 * <li>Public: health, {@code GET /auth/csrf}, {@code POST /auth/sign-in}; every
 * other {@code /api/**} request needs a session; anything else is denied.</li>
 * <li>Sessions: Spring Session JDBC, cookie {@code matriz_session}, idle and
 * absolute timeouts; no anonymous sessions (request cache disabled).</li>
 * <li>CSRF: cookie-to-header ({@code XSRF-TOKEN} → {@code X-XSRF-TOKEN}) on
 * every unsafe method, sign-in and sign-out included.</li>
 * <li>401/403 are rendered by the MVC exception handlers as problem JSON.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AuthProperties.class)
class SecurityConfig {

	static final String SESSION_COOKIE = "matriz_session";

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CookieCsrfTokenRepository csrfTokenRepository,
			SecurityContextRepository securityContextRepository, AuthProperties properties, Clock clock,
			@Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) throws Exception {
		CsrfTokenRequestAttributeHandler csrfRequestHandler = new CsrfTokenRequestAttributeHandler();
		// Load the token on every request so the XSRF-TOKEN cookie is always (re)issued when missing.
		csrfRequestHandler.setCsrfRequestAttributeName(null);
		return http
			.authorizeHttpRequests(auth -> auth
				.requestMatchers(HttpMethod.GET, "/actuator/health").permitAll()
				.requestMatchers(HttpMethod.GET, "/api/v1/auth/csrf").permitAll()
				.requestMatchers(HttpMethod.POST, "/api/v1/auth/sign-in").permitAll()
				.requestMatchers("/api/**").authenticated()
				.anyRequest().denyAll())
			.csrf(csrf -> csrf.csrfTokenRepository(csrfTokenRepository).csrfTokenRequestHandler(csrfRequestHandler))
			.securityContext(context -> context.securityContextRepository(securityContextRepository))
			.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED))
			.requestCache(AbstractHttpConfigurer::disable)
			.addFilterBefore(new AbsoluteSessionTimeoutFilter(properties.absoluteSessionTimeout(), clock),
					SecurityContextHolderFilter.class)
			.exceptionHandling(errors -> errors
				.authenticationEntryPoint((request, response, ex) -> exceptionResolver.resolveException(request,
						response, null, ex))
				.accessDeniedHandler(
						(request, response, ex) -> exceptionResolver.resolveException(request, response, null, ex)))
			.httpBasic(AbstractHttpConfigurer::disable)
			.formLogin(AbstractHttpConfigurer::disable)
			.logout(AbstractHttpConfigurer::disable)
			.build();
	}

	@Bean
	CookieCsrfTokenRepository csrfTokenRepository(AuthProperties properties) {
		CookieCsrfTokenRepository repository = CookieCsrfTokenRepository.withHttpOnlyFalse();
		repository.setCookieCustomizer(cookie -> cookie.path("/").sameSite("Lax").secure(properties.secureCookies()));
		return repository;
	}

	/**
	 * The Spring Session cookie, declared explicitly so it is identical with or
	 * without an embedded server (Boot only applies {@code server.servlet.session.cookie.*}
	 * to an embedded server).
	 */
	@Bean
	CookieSerializer sessionCookieSerializer(AuthProperties properties) {
		DefaultCookieSerializer serializer = new DefaultCookieSerializer();
		serializer.setCookieName(SESSION_COOKIE);
		serializer.setCookiePath("/");
		serializer.setUseHttpOnlyCookie(true);
		serializer.setSameSite("Lax");
		serializer.setUseSecureCookie(properties.secureCookies());
		return serializer;
	}

	@Bean
	SecurityContextRepository securityContextRepository() {
		return new HttpSessionSecurityContextRepository();
	}

	/** Argon2id with Spring Security's current recommended parameters (ADR-0009). */
	@Bean
	PasswordEncoder passwordEncoder() {
		return Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
	}

}
