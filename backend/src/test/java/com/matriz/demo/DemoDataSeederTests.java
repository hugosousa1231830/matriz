package com.matriz.demo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.TestcontainersConfiguration;

/** The demo seed is reproducible, idempotent and never stores plain passwords. */
@SpringBootTest(properties = { "matriz.demo.seed=true", "matriz.demo.password=" + DemoDataSeederTests.PASSWORD })
@Import(TestcontainersConfiguration.class)
class DemoDataSeederTests {

	static final String PASSWORD = "test-only-demo-password";

	@Autowired
	JdbcClient jdbc;

	@Autowired
	PasswordEncoder passwordEncoder;

	@Autowired
	DemoDataSeeder seeder;

	@Test
	void seedsDemoDataAtStartup() {
		assertThat(counts()).isEqualTo(expectedCounts());
		assertThat(jdbc.sql("SELECT email FROM users WHERE is_admin").query(String.class).list())
			.containsExactly("vitor@matriz.test");
	}

	@Test
	void storesOnlyArgon2idHashesOfTheDemoPassword() {
		List<String> hashes = jdbc.sql("SELECT password_hash FROM users").query(String.class).list();

		assertThat(hashes).hasSize(5).allSatisfy(hash -> {
			assertThat(hash).startsWith("$argon2id$").doesNotContain(PASSWORD);
			assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
		});
	}

	@Test
	void providesUsersWithDistinctAccess() {
		assertThat(role("ana@matriz.test", "Website Redesign")).containsExactly("MANAGER");
		assertThat(role("rui@matriz.test", "Website Redesign")).containsExactly("MEMBER");
		assertThat(role("marta@matriz.test", "Website Redesign")).isEmpty();
		assertThat(role("marta@matriz.test", "Mobile App Launch")).containsExactly("MANAGER");
		assertThat(role("ana@matriz.test", "Mobile App Launch")).isEmpty();
		assertThat(jdbc.sql("""
				SELECT count(*) FROM project_member m JOIN users u ON u.id = m.user_id
				WHERE u.email IN ('joao@matriz.test', 'vitor@matriz.test')""").query(Long.class).single()).isZero();
		assertThat(jdbc.sql("SELECT name FROM project WHERE status = 'ARCHIVED'").query(String.class).list())
			.containsExactly("Legacy Intranet");
	}

	@Test
	void runningAgainDoesNotDuplicateOrChangeData() {
		List<String> idsBefore = jdbc.sql("SELECT id::text FROM users ORDER BY id").query(String.class).list();

		assertThat(seeder.seedIfEmpty()).isFalse();

		assertThat(counts()).isEqualTo(expectedCounts());
		assertThat(jdbc.sql("SELECT id::text FROM users ORDER BY id").query(String.class).list())
			.isEqualTo(idsBefore);
	}

	@Test
	@Transactional
	void doesNothingWhenDisabled() {
		deleteAllRows();

		DemoDataSeeder disabled = new DemoDataSeeder(jdbc, passwordEncoder, new DemoProperties(false, PASSWORD));

		assertThat(disabled.seedIfEmpty()).isFalse();
		assertThat(jdbc.sql("SELECT count(*) FROM users").query(Long.class).single()).isZero();
	}

	@Test
	@Transactional
	void refusesToSeedWithoutPassword() {
		deleteAllRows();

		DemoDataSeeder noPassword = new DemoDataSeeder(jdbc, passwordEncoder, new DemoProperties(true, " "));

		assertThatThrownBy(noPassword::seedIfEmpty).isInstanceOf(IllegalStateException.class)
			.hasMessageContaining("MATRIZ_DEMO_PASSWORD");
	}

	private List<String> role(String email, String project) {
		return jdbc.sql("""
				SELECT m.role FROM project_member m
				JOIN users u ON u.id = m.user_id JOIN project p ON p.id = m.project_id
				WHERE u.email = ? AND p.name = ?""").params(email, project).query(String.class).list();
	}

	private Map<String, Long> counts() {
		return Map.of("users", count("users"), "project", count("project"), "project_member",
				count("project_member"), "task", count("task"), "task_assignee", count("task_assignee"), "comment",
				count("comment"));
	}

	private static Map<String, Long> expectedCounts() {
		return Map.of("users", 5L, "project", 3L, "project_member", 4L, "task", 9L, "task_assignee", 5L, "comment",
				4L);
	}

	private long count(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

	/** Rolled back by the surrounding test transaction. */
	private void deleteAllRows() {
		for (String table : List.of("comment", "task_assignee", "task", "project_member", "project", "users")) {
			jdbc.sql("DELETE FROM " + table).update();
		}
	}

}
