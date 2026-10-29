package com.matriz.identity;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;

import com.matriz.shared.ApiException;
import com.matriz.shared.ProblemCode;

/**
 * Per-client sign-in rate limit: a sliding one-minute window of attempts per
 * client IP, kept in memory (single instance, ADR-0012). Rejected attempts are
 * not counted.
 */
@Component
class SignInRateLimiter {

	private static final Duration WINDOW = Duration.ofMinutes(1);

	private static final int CLEANUP_THRESHOLD = 10_000;

	private final Map<String, Deque<Instant>> attempts = new ConcurrentHashMap<>();

	private final int maxAttempts;

	private final Clock clock;

	SignInRateLimiter(AuthProperties properties, Clock clock) {
		this.maxAttempts = properties.maxAttemptsPerMinute();
		this.clock = clock;
	}

	/** Records an attempt from {@code clientIp} or throws {@code 429 TOO_MANY_ATTEMPTS}. */
	void acquire(String clientIp) {
		Instant now = clock.instant();
		if (attempts.size() > CLEANUP_THRESHOLD) {
			attempts.entrySet().removeIf(e -> {
				synchronized (e.getValue()) {
					purge(e.getValue(), now);
					return e.getValue().isEmpty();
				}
			});
		}
		Deque<Instant> window = attempts.computeIfAbsent(clientIp, ip -> new ArrayDeque<>());
		synchronized (window) {
			purge(window, now);
			if (window.size() >= maxAttempts) {
				long retryAfter = Math.max(1,
						Duration.between(now, window.peekFirst().plus(WINDOW)).toSeconds() + 1);
				HttpHeaders headers = new HttpHeaders();
				headers.set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
				throw new ApiException(ProblemCode.TOO_MANY_ATTEMPTS, "Too many sign-in attempts. Try again later.",
						headers);
			}
			window.addLast(now);
		}
	}

	private static void purge(Deque<Instant> window, Instant now) {
		Instant threshold = now.minus(WINDOW);
		while (!window.isEmpty() && !window.peekFirst().isAfter(threshold)) {
			window.removeFirst();
		}
	}

}
