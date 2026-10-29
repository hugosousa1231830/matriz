package com.matriz.identity;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.shared.ApiException;
import com.matriz.shared.ProblemCode;

/**
 * Verifies credentials with account lockout. Every failure (unknown email,
 * wrong password, inactive or locked account) produces the same generic
 * {@code INVALID_CREDENTIALS} error, and a password hash is always checked so
 * the response time does not reveal whether the account exists.
 */
@Service
class SignInService {

	private static final Logger log = LoggerFactory.getLogger(SignInService.class);

	private final UserAccountRepository users;

	private final PasswordEncoder passwordEncoder;

	private final AuthProperties properties;

	private final Clock clock;

	/** Hash of a random value, checked when the email is unknown (constant-time behaviour). */
	private final String dummyHash;

	SignInService(UserAccountRepository users, PasswordEncoder passwordEncoder, AuthProperties properties,
			Clock clock) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode(UUID.randomUUID().toString());
	}

	@Transactional(noRollbackFor = ApiException.class)
	public AuthenticatedUser signIn(String email, String password) {
		Instant now = clock.instant();
		Optional<UserAccount> found = users.findByEmail(email.strip());
		if (found.isEmpty()) {
			passwordEncoder.matches(password, dummyHash);
			throw invalidCredentials();
		}
		UserAccount user = found.get();
		boolean passwordMatches = passwordEncoder.matches(password, user.getPasswordHash());
		if (!user.isActive() || user.isLockedAt(now)) {
			throw invalidCredentials();
		}
		if (!passwordMatches) {
			users.recordFailedSignIn(user.getId(), properties.maxFailedAttempts(),
					now.plus(properties.lockDuration()));
			if (user.getFailedLoginAttempts() + 1 >= properties.maxFailedAttempts()) {
				log.warn("Account {} locked after {} failed sign-in attempts", user.getId(),
						properties.maxFailedAttempts());
			}
			throw invalidCredentials();
		}
		users.resetFailedSignIns(user.getId());
		return user.toAuthenticatedUser();
	}

	/** Current state of a signed-in user; empty if the account no longer exists or is inactive. */
	@Transactional(readOnly = true)
	public Optional<AuthenticatedUser> currentUser(UUID id) {
		return users.findById(id).filter(UserAccount::isActive).map(UserAccount::toAuthenticatedUser);
	}

	private static ApiException invalidCredentials() {
		return new ApiException(ProblemCode.INVALID_CREDENTIALS);
	}

}
