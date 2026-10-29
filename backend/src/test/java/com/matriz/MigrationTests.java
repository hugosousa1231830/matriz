package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

/** The Flyway migrations create the release 1 schema with its integrity rules. */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class MigrationTests {

	@Autowired
	JdbcClient jdbc;

	@Test
	void appliesAllMigrations() {
		List<String> versions = jdbc
			.sql("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank")
			.query(String.class)
			.list();
		assertThat(versions).containsExactly("1", "2");
	}

	@Test
	void createsDomainAndSessionTables() {
		List<String> tables = jdbc.sql("""
				SELECT table_name FROM information_schema.tables
				WHERE table_schema = 'public' AND table_type = 'BASE TABLE'""").query(String.class).list();
		assertThat(tables).contains("users", "project", "project_member", "task", "task_assignee", "comment",
				"spring_session", "spring_session_attributes");
	}

	@Test
	void rejectsDuplicateEmailIgnoringCase() {
		insertUser("dup@matriz.test");
		assertThatThrownBy(() -> insertUser("DUP@matriz.test")).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void appliesTaskDefaults() {
		UUID user = insertUser("defaults@matriz.test");
		UUID task = insertTask(insertProject(user), user, "Valid title", "TODO");

		String defaults = jdbc.sql("SELECT status || '/' || priority || '/' || version FROM task WHERE id = ?")
			.param(task)
			.query(String.class)
			.single();
		assertThat(defaults).isEqualTo("TODO/MEDIUM/1");
	}

	@Test
	void rejectsUnknownTaskStatus() {
		UUID user = insertUser("status@matriz.test");
		UUID project = insertProject(user);

		assertThatThrownBy(() -> insertTask(project, user, "Valid title", "BLOCKED"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsWhitespaceOnlyTaskTitle() {
		UUID user = insertUser("title@matriz.test");
		UUID project = insertProject(user);

		assertThatThrownBy(() -> insertTask(project, user, "     ", "TODO"))
			.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void rejectsDuplicateAssignment() {
		UUID user = insertUser("assign@matriz.test");
		UUID task = insertTask(insertProject(user), user, "Valid title", "TODO");
		assign(task, user);

		assertThatThrownBy(() -> assign(task, user)).isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void deletingTaskCascadesToAssignmentsAndComments() {
		UUID user = insertUser("cascade@matriz.test");
		UUID task = insertTask(insertProject(user), user, "Cascade task", "TODO");
		assign(task, user);
		jdbc.sql("INSERT INTO comment (id, task_id, author_id, body) VALUES (?, ?, ?, 'Hello')")
			.params(UUID.randomUUID(), task, user)
			.update();

		jdbc.sql("DELETE FROM task WHERE id = ?").param(task).update();

		assertThat(count("SELECT count(*) FROM task_assignee WHERE task_id = ?", task)).isZero();
		assertThat(count("SELECT count(*) FROM comment WHERE task_id = ?", task)).isZero();
	}

	private UUID insertUser(String email) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO users (id, email, name, password_hash) VALUES (?, ?, 'Test User', 'not-a-real-hash')")
			.params(id, email)
			.update();
		return id;
	}

	private UUID insertProject(UUID owner) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO project (id, name, owner_id) VALUES (?, 'Schema test', ?)").params(id, owner).update();
		return id;
	}

	private void assign(UUID task, UUID user) {
		jdbc.sql("INSERT INTO task_assignee (task_id, user_id, assigned_by_id) VALUES (?, ?, ?)")
			.params(task, user, user)
			.update();
	}

	private UUID insertTask(UUID project, UUID user, String title, String status) {
		UUID id = UUID.randomUUID();
		jdbc.sql("INSERT INTO task (id, project_id, title, status, created_by_id) VALUES (?, ?, ?, ?, ?)")
			.params(id, project, title, status, user)
			.update();
		return id;
	}

	private long count(String sql, Object param) {
		return jdbc.sql(sql).param(param).query(Long.class).single();
	}

}
