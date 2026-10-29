package com.matriz.identity;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.shared.Text;

/**
 * Read access to users for the other modules (they never read the
 * {@code users} table directly).
 */
@Component
public class UserDirectory {

	private final JdbcClient jdbc;

	UserDirectory(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	/** Contacts of the given users (active or not), by id. */
	@Transactional(readOnly = true)
	public Map<UUID, UserContact> contacts(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Map.of();
		}
		return jdbc.sql("SELECT id, name, email FROM users WHERE id IN (:ids)")
			.param("ids", ids)
			.query(UserContact.class)
			.list()
			.stream()
			.collect(Collectors.toMap(UserContact::id, Function.identity()));
	}

	/** The ids among {@code ids} of users who exist and are active. */
	@Transactional(readOnly = true)
	public Set<UUID> activeIds(Collection<UUID> ids) {
		if (ids.isEmpty()) {
			return Set.of();
		}
		return Set.copyOf(jdbc.sql("SELECT id FROM users WHERE id IN (:ids) AND is_active")
			.param("ids", ids)
			.query(UUID.class)
			.list());
	}

	/** The user if it exists and is active. */
	@Transactional(readOnly = true)
	public Optional<UserContact> findActive(UUID id) {
		return jdbc.sql("SELECT id, name, email FROM users WHERE id = :id AND is_active")
			.param("id", id)
			.query(UserContact.class)
			.optional();
	}

	/**
	 * Active users whose name or email contains {@code term} (case-insensitive),
	 * excluding {@code excludedIds}, sorted by name.
	 */
	@Transactional(readOnly = true)
	public List<UserContact> searchActive(String term, Collection<UUID> excludedIds, int limit) {
		String exclusion = excludedIds.isEmpty() ? "" : "AND id NOT IN (:excluded)";
		var spec = jdbc.sql("""
				SELECT id, name, email FROM users
				WHERE is_active
				  AND (lower(name) LIKE :pattern ESCAPE '\\' OR lower(email) LIKE :pattern ESCAPE '\\')
				  %s
				ORDER BY lower(name), id
				LIMIT :limit""".formatted(exclusion))
			.param("pattern", Text.containsPattern(term))
			.param("limit", limit);
		if (!excludedIds.isEmpty()) {
			spec = spec.param("excluded", excludedIds);
		}
		return spec.query(UserContact.class).list();
	}

}
