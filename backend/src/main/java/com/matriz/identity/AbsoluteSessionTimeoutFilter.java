package com.matriz.identity;

import java.io.IOException;
import java.time.Clock;
import java.time.Duration;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Ends sessions older than the absolute lifetime (8 h), whatever the activity.
 * Spring Session only enforces the idle timeout. A new session is created at
 * every sign-in, so the session creation time is the sign-in time. The request
 * then continues unauthenticated (protected endpoints answer {@code 401}).
 */
class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

	private final Duration absoluteTimeout;

	private final Clock clock;

	AbsoluteSessionTimeoutFilter(Duration absoluteTimeout, Clock clock) {
		this.absoluteTimeout = absoluteTimeout;
		this.clock = clock;
	}

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		HttpSession session = request.getSession(false);
		if (session != null && clock.millis() - session.getCreationTime() > absoluteTimeout.toMillis()) {
			session.invalidate();
		}
		chain.doFilter(request, response);
	}

}
