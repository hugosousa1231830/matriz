package com.matriz.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.matriz.ApiIntegrationTest;

/** Project membership over HTTP: list, add, change role, remove, candidates. */
class MemberApiTests extends ApiIntegrationTest {

	private Session ana;

	private UUID anaId;

	private UUID ruiId;

	private UUID joaoId;

	private UUID project;

	@BeforeEach
	void createProject() throws Exception {
		ana = signIn("ana@matriz.test");
		anaId = userId("ana@matriz.test");
		ruiId = userId("rui@matriz.test");
		joaoId = userId("joao@matriz.test");
		String body = mvc.perform(post("/api/v1/projects").with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Team " + UUID.randomUUID() + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		project = UUID.fromString(JsonPath.read(body, "$.id"));
	}

	@Test
	void managerAddsListsAndChangesRoles() throws Exception {
		add(ana, ruiId, null).andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.user.email").value("rui@matriz.test"))
			.andExpect(jsonPath("$.role").value("MEMBER"))
			.andExpect(jsonPath("$.isOwner").value(false));
		add(ana, joaoId, "MANAGER").andExpect(status().isCreated()).andExpect(jsonPath("$.role").value("MANAGER"));

		mvc.perform(get("/api/v1/projects/{p}/members", project).with(signIn("rui@matriz.test").auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].user.name").value("Ana Costa"))
			.andExpect(jsonPath("$[0].isOwner").value(true))
			.andExpect(jsonPath("$[1].user.name").value("João Ferreira"))
			.andExpect(jsonPath("$[2].user.name").value("Rui Pereira"));

		changeRole(ana, ruiId, "MANAGER").andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.role").value("MANAGER"));
		// The new manager can now manage members.
		changeRole(signIn("rui@matriz.test"), joaoId, "MEMBER").andExpect(status().isOk());
		mvc.perform(get("/api/v1/projects/{p}", project).with(ana.auth()))
			.andExpect(jsonPath("$.memberCount").value(3));
	}

	@Test
	void addRejectsDuplicatesAndIneligibleUsers() throws Exception {
		add(ana, ruiId, "MEMBER").andExpect(status().isCreated());
		add(ana, ruiId, "MANAGER").andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("ALREADY_MEMBER"));
		add(ana, UUID.randomUUID(), null).andExpect(status().isUnprocessableContent())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("USER_NOT_ELIGIBLE"));

		UUID inactive = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO users (id, email, name, password_hash, is_active)
				VALUES (:id, :email, 'Inactive Person', 'x', false)
				""").param("id", inactive).param("email", "inactive-" + inactive + "@matriz.test").update();
		add(ana, inactive, null).andExpect(status().isUnprocessableContent());

		mvc.perform(post("/api/v1/projects/{p}/members", project).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"" + joaoId + "\",\"role\":\"OWNER\"}")).andExpect(status().isBadRequest());
		mvc.perform(post("/api/v1/projects/{p}/members", project).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors[0].field").value("userId"));
	}

	@Test
	void listFlagsDeactivatedMembers() throws Exception {
		add(ana, ruiId, null).andExpect(status().isCreated()).andExpect(jsonPath("$.active").value(true));
		UUID inactive = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO users (id, email, name, password_hash, is_active)
				VALUES (:id, :email, 'Zed Inactive', 'x', false)
				""").param("id", inactive).param("email", "inactive-" + inactive + "@matriz.test").update();
		jdbc.sql("INSERT INTO project_member (project_id, user_id, role) VALUES (:p, :u, 'MEMBER')")
			.param("p", project)
			.param("u", inactive)
			.update();

		mvc.perform(get("/api/v1/projects/{p}/members", project).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$", hasSize(3)))
			.andExpect(jsonPath("$[0].active").value(true))
			.andExpect(jsonPath("$[1].active").value(true))
			.andExpect(jsonPath("$[2].user.name").value("Zed Inactive"))
			.andExpect(jsonPath("$[2].active").value(false));
		changeRole(ana, inactive, "MANAGER").andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.active").value(false));
	}

	@Test
	void ownerCannotBeDemotedOrRemoved() throws Exception {
		changeRole(ana, anaId, "MEMBER").andExpect(status().isUnprocessableContent())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("OWNER_IMMUTABLE"));
		changeRole(ana, anaId, "MANAGER").andExpect(status().isOk()).andExpect(jsonPath("$.isOwner").value(true));
		remove(signIn("vitor@matriz.test"), anaId).andExpect(status().isUnprocessableContent())
			.andExpect(matchesContract());
		changeRole(ana, joaoId, "MEMBER").andExpect(status().isNotFound());
		remove(ana, joaoId).andExpect(status().isNotFound());
	}

	@Test
	void onlyManagersManageMembers() throws Exception {
		add(ana, ruiId, "MEMBER").andExpect(status().isCreated());
		Session rui = signIn("rui@matriz.test");
		Session marta = signIn("marta@matriz.test");

		add(rui, joaoId, null).andExpect(status().isForbidden()).andExpect(matchesContract());
		changeRole(rui, ruiId, "MANAGER").andExpect(status().isForbidden());
		remove(rui, anaId).andExpect(status().isForbidden());
		candidates(rui, "jo").andExpect(status().isForbidden()).andExpect(matchesContract());
		add(marta, joaoId, null).andExpect(status().isNotFound()).andExpect(matchesContract());
		mvc.perform(get("/api/v1/projects/{p}/members", project).with(marta.auth()))
			.andExpect(status().isNotFound());
		add(signIn("vitor@matriz.test"), joaoId, null).andExpect(status().isCreated());
	}

	@Test
	void removingAMemberDeletesTheirAssignmentsButKeepsCommentsAndTasks() throws Exception {
		add(ana, ruiId, "MEMBER").andExpect(status().isCreated());
		UUID task = UUID.randomUUID();
		jdbc.sql("INSERT INTO task (id, project_id, title, created_by_id) VALUES (:id, :p, 'Fix the fence', :u)")
			.param("id", task)
			.param("p", project)
			.param("u", anaId)
			.update();
		jdbc.sql("INSERT INTO task_assignee (task_id, user_id, assigned_by_id) VALUES (:t, :u, :by)")
			.param("t", task)
			.param("u", ruiId)
			.param("by", anaId)
			.update();
		jdbc.sql("INSERT INTO comment (id, task_id, author_id, body) VALUES (:id, :t, :u, 'On it')")
			.param("id", UUID.randomUUID())
			.param("t", task)
			.param("u", ruiId)
			.update();
		long otherAssignments = assignmentsOf(ruiId);

		remove(ana, ruiId).andExpect(status().isNoContent()).andExpect(matchesContract());

		assertThat(assignmentsOf(ruiId)).isEqualTo(otherAssignments - 1);
		assertThat(count("SELECT count(*) FROM task_assignee WHERE task_id = :t", task)).isZero();
		assertThat(count("SELECT count(*) FROM comment WHERE task_id = :t", task)).isOne();
		assertThat(count("SELECT count(*) FROM task WHERE id = :t", task)).isOne();
		mvc.perform(get("/api/v1/projects/{p}", project).with(signIn("rui@matriz.test").auth()))
			.andExpect(status().isNotFound());
		remove(ana, ruiId).andExpect(status().isNotFound());
	}

	@Test
	void archivedProjectMembershipIsReadOnly() throws Exception {
		add(ana, ruiId, "MEMBER").andExpect(status().isCreated());
		mvc.perform(post("/api/v1/projects/{p}/archive", project).with(ana.auth())).andExpect(status().isOk());

		add(ana, joaoId, null).andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		changeRole(ana, ruiId, "MANAGER").andExpect(status().isConflict()).andExpect(matchesContract());
		remove(ana, ruiId).andExpect(status().isConflict()).andExpect(matchesContract());
		mvc.perform(get("/api/v1/projects/{p}/members", project).with(ana.auth())).andExpect(status().isOk());
		candidates(ana, "jo").andExpect(status().isOk());
	}

	@Test
	void candidatesAreActiveNonMembersMatchingNameOrEmail() throws Exception {
		add(ana, ruiId, "MEMBER").andExpect(status().isCreated());

		candidates(ana, " JO ").andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$[*].email").value(hasItem("joao@matriz.test")));
		candidates(ana, "matriz.test").andExpect(status().isOk())
			.andExpect(jsonPath("$[*].email").value(not(hasItem("rui@matriz.test"))))
			.andExpect(jsonPath("$[*].email").value(not(hasItem("ana@matriz.test"))))
			.andExpect(jsonPath("$[*].email").value(hasItem("marta@matriz.test")));
		candidates(ana, "Inactive Person").andExpect(jsonPath("$", hasSize(0)));
		candidates(ana, "j").andExpect(status().isBadRequest());
		candidates(ana, "  j ").andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/projects/{p}/member-candidates", project).with(ana.auth()))
			.andExpect(status().isBadRequest());
	}

	private ResultActions add(Session session, UUID userId, String role) throws Exception {
		String json = role == null ? "{\"userId\":\"" + userId + "\"}"
				: "{\"userId\":\"" + userId + "\",\"role\":\"" + role + "\"}";
		return mvc.perform(post("/api/v1/projects/{p}/members", project).with(session.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions changeRole(Session session, UUID userId, String role) throws Exception {
		return mvc.perform(patch("/api/v1/projects/{p}/members/{u}", project, userId).with(session.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"role\":\"" + role + "\"}"));
	}

	private ResultActions remove(Session session, UUID userId) throws Exception {
		return mvc.perform(delete("/api/v1/projects/{p}/members/{u}", project, userId).with(session.auth()));
	}

	private ResultActions candidates(Session session, String search) throws Exception {
		return mvc.perform(get("/api/v1/projects/{p}/member-candidates", project).param("search", search)
			.with(session.auth()));
	}

	private long assignmentsOf(UUID user) {
		return count("SELECT count(*) FROM task_assignee WHERE user_id = :t", user);
	}

	private long count(String sql, UUID param) {
		return jdbc.sql(sql).param("t", param).query(Long.class).single();
	}

	private UUID userId(String email) {
		return jdbc.sql("SELECT id FROM users WHERE email = :e").param("e", email).query(UUID.class).single();
	}

}
