package com.matriz.identity;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface UserAccountRepository extends JpaRepository<UserAccount, UUID> {

	/** Case-insensitive lookup (uses the unique index on {@code lower(email)}). */
	@Query("select u from UserAccount u where lower(u.email) = lower(:email)")
	Optional<UserAccount> findByEmail(@Param("email") String email);

	/**
	 * Atomically counts a failed sign-in; the attempt that reaches
	 * {@code maxAttempts} locks the account until {@code lockUntil} and restarts
	 * the counter.
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE users
			SET locked_until = CASE WHEN failed_login_attempts + 1 >= :maxAttempts THEN :lockUntil ELSE locked_until END,
			    failed_login_attempts = CASE WHEN failed_login_attempts + 1 >= :maxAttempts THEN 0
			                                 ELSE failed_login_attempts + 1 END
			WHERE id = :id""", nativeQuery = true)
	void recordFailedSignIn(@Param("id") UUID id, @Param("maxAttempts") int maxAttempts,
			@Param("lockUntil") Instant lockUntil);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = "UPDATE users SET failed_login_attempts = 0, locked_until = NULL WHERE id = :id", nativeQuery = true)
	void resetFailedSignIns(@Param("id") UUID id);

}
