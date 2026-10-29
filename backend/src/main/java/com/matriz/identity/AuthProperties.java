package com.matriz.identity;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Session and brute-force protection settings ({@code matriz.auth.*}). The
 * idle timeout is Spring Session's {@code server.servlet.session.timeout}.
 *
 * @param absoluteSessionTimeout maximum session lifetime from sign-in, whatever the activity
 * @param maxFailedAttempts      consecutive failed sign-ins that lock an account
 * @param lockDuration           how long a locked account rejects sign-in
 * @param maxAttemptsPerMinute   sign-in attempts accepted per client IP and minute
 * @param secureCookies          send cookies with the {@code Secure} flag (off only for local HTTP)
 */
@ConfigurationProperties("matriz.auth")
record AuthProperties(@DefaultValue("8h") Duration absoluteSessionTimeout, @DefaultValue("5") int maxFailedAttempts,
		@DefaultValue("15m") Duration lockDuration, @DefaultValue("20") int maxAttemptsPerMinute,
		@DefaultValue("true") boolean secureCookies) {
}
