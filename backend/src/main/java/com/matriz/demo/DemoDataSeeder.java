package com.matriz.demo;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/**
 * Inserts reproducible, fictitious demo data at startup.
 * <p>
 * Runs only when {@code matriz.demo.seed=true} and the {@code users} table is
 * empty, so restarts never duplicate, overwrite or delete data. Like a
 * migration, it writes the tables of several modules directly, inside one
 * transaction. Accounts use the reserved {@code .test} domain and share one
 * password that is stored only as an Argon2id hash.
 */
@Component
class DemoDataSeeder implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

	private final JdbcClient jdbc;

	private final PasswordEncoder passwordEncoder;

	private final DemoProperties properties;

	DemoDataSeeder(JdbcClient jdbc, PasswordEncoder passwordEncoder, DemoProperties properties) {
		this.jdbc = jdbc;
		this.passwordEncoder = passwordEncoder;
		this.properties = properties;
	}

	@Override
	@Transactional
	public void run(ApplicationArguments args) {
		seedIfEmpty();
	}

	/** @return {@code true} if demo data was inserted. */
	@Transactional
	public boolean seedIfEmpty() {
		if (!properties.seed()) {
			log.info("Demo data seeding disabled (MATRIZ_SEED_DEMO is not true)");
			return false;
		}
		long users = jdbc.sql("SELECT count(*) FROM users").query(Long.class).single();
		if (users > 0) {
			log.info("Demo data seeding skipped: database already contains users");
			return false;
		}
		if (!StringUtils.hasText(properties.password())) {
			throw new IllegalStateException(
					"MATRIZ_SEED_DEMO=true requires MATRIZ_DEMO_PASSWORD to be set (see .env.example)");
		}
		insertDemoData(passwordEncoder.encode(properties.password()));
		log.info("Demo data seeded: 5 users, 3 projects, 9 tasks, 4 comments");
		return true;
	}

	private void insertDemoData(String passwordHash) {
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		user("vitor@matriz.test", "Vitor Almeida", true, passwordHash, now.minusDays(30));
		UUID ana = user("ana@matriz.test", "Ana Costa", false, passwordHash, now.minusDays(30));
		UUID rui = user("rui@matriz.test", "Rui Pereira", false, passwordHash, now.minusDays(30));
		UUID marta = user("marta@matriz.test", "Marta Silva", false, passwordHash, now.minusDays(30));
		user("joao@matriz.test", "João Ferreira", false, passwordHash, now.minusDays(30));

		// Website Redesign: Ana manages, Rui is a member; Marta, João and Vitor are not members
		// (Vitor, as Administrator, still sees it).
		UUID website = project("Website Redesign",
				"Refresh the public website: new structure, visual design and content.", ana, null,
				now.minusDays(20));
		member(website, ana, "MANAGER", now.minusDays(20));
		member(website, rui, "MEMBER", now.minusDays(19));
		UUID siteMap = task(website, "Define site map", "Agree on the pages and navigation of the new site.",
				"DONE", "HIGH", ana, now.minusDays(18));
		UUID mockups = task(website, "Design homepage mockups", "Desktop and mobile mockups for review.",
				"IN_PROGRESS", "HIGH", ana, now.minusDays(15));
		UUID aboutPage = task(website, "Write content for About page", null, "TODO", "MEDIUM", ana,
				now.minusDays(12));
		task(website, "Set up analytics", "Privacy-friendly page view statistics.", "TODO", "LOW", ana,
				now.minusDays(10));
		task(website, "Accessibility review", null, "TODO", "MEDIUM", ana, now.minusDays(8));
		assign(siteMap, ana, ana, now.minusDays(18));
		assign(mockups, ana, ana, now.minusDays(15));
		assign(mockups, rui, ana, now.minusDays(15));
		assign(aboutPage, rui, ana, now.minusDays(12));
		comment(mockups, ana, "Please share the first draft by Friday.", now.minusDays(14));
		comment(mockups, rui, "First draft uploaded to the shared folder.", now.minusDays(11));
		comment(aboutPage, rui, "Waiting for the updated brand guidelines.", now.minusDays(9));

		// Mobile App Launch: Marta manages; Ana and Rui are outsiders here.
		UUID mobile = project("Mobile App Launch", "Publish the first version of the mobile app.", marta, null,
				now.minusDays(16));
		member(mobile, marta, "MANAGER", now.minusDays(16));
		UUID storeListing = task(mobile, "Prepare app store listing", "Screenshots, description and keywords.",
				"IN_PROGRESS", "HIGH", marta, now.minusDays(14));
		task(mobile, "Run beta testing round", null, "TODO", "MEDIUM", marta, now.minusDays(10));
		task(mobile, "Write release notes", null, "TODO", "LOW", marta, now.minusDays(6));
		assign(storeListing, marta, marta, now.minusDays(14));
		comment(storeListing, marta, "Screenshots are ready; description still in progress.", now.minusDays(7));

		// Legacy Intranet: archived (read-only) project managed by Marta.
		UUID intranet = project("Legacy Intranet", "Decommissioned internal portal, kept for reference.", marta,
				now.minusDays(5), now.minusDays(29));
		member(intranet, marta, "MANAGER", now.minusDays(29));
		task(intranet, "Export old documents", null, "DONE", "MEDIUM", marta, now.minusDays(28));
	}

	private UUID user(String email, String name, boolean admin, String passwordHash, OffsetDateTime createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO users (id, email, name, password_hash, is_admin, created_at)
				VALUES (?, ?, ?, ?, ?, ?)""")
			.params(id, email, name, passwordHash, admin, createdAt)
			.update();
		return id;
	}

	private UUID project(String name, String description, UUID owner, OffsetDateTime archivedAt,
			OffsetDateTime createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO project (id, name, description, owner_id, status, archived_at, created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?)""")
			.params(id, name, description, owner, archivedAt == null ? "ACTIVE" : "ARCHIVED", archivedAt, createdAt,
					archivedAt == null ? createdAt : archivedAt)
			.update();
		return id;
	}

	private void member(UUID project, UUID user, String role, OffsetDateTime joinedAt) {
		jdbc.sql("INSERT INTO project_member (project_id, user_id, role, joined_at) VALUES (?, ?, ?, ?)")
			.params(project, user, role, joinedAt)
			.update();
	}

	private UUID task(UUID project, String title, String description, String status, String priority,
			UUID createdBy, OffsetDateTime createdAt) {
		UUID id = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO task (id, project_id, title, description, status, priority, created_by_id,
				                  created_at, updated_at)
				VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)""")
			.params(id, project, title, description, status, priority, createdBy, createdAt, createdAt)
			.update();
		return id;
	}

	private void assign(UUID task, UUID user, UUID assignedBy, OffsetDateTime assignedAt) {
		jdbc.sql("""
				INSERT INTO task_assignee (task_id, user_id, assigned_at, assigned_by_id)
				VALUES (?, ?, ?, ?)""")
			.params(task, user, assignedAt, assignedBy)
			.update();
	}

	private void comment(UUID task, UUID author, String body, OffsetDateTime createdAt) {
		jdbc.sql("INSERT INTO comment (id, task_id, author_id, body, created_at) VALUES (?, ?, ?, ?, ?)")
			.params(UUID.randomUUID(), task, author, body, createdAt)
			.update();
	}

}
