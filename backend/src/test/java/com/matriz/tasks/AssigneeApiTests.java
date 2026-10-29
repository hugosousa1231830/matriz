package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.matriz.ApiIntegrationTest;

/**
 * Adding and removing task assignees over HTTP, checked against openapi.yaml.
 * Each test works on its own project (Ana = Manager, Rui = Member, Marta =
 * outsider, Vitor = Administrator and not a member).
 */
class AssigneeApiTests extends ApiIntegrationTest {

	@Test
	void taskHasSeveralAssigneesThatAreRemovedOneByOne() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Assignee Lifecycle");
		addMember(project, "rui@matriz.test");
		UUID task = task(ana, project);
		UUID anaId = userId("ana@matriz.test");
		UUID ruiId = userId("rui@matriz.test");

		assign(ana, task, ruiId).andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.assignees.length()").value(1))
			.andExpect(jsonPath("$.assignees[0].name").value("Rui Pereira"))
			.andExpect(jsonPath("$.version").value(1));
		assign(ana, task, anaId).andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.assignees.length()").value(2));
		assertThat(assigneeCount(task)).isEqualTo(2);
		assertThat(jdbc.sql("SELECT assigned_by_id FROM task_assignee WHERE task_id = :t AND user_id = :u")
			.param("t", task)
			.param("u", ruiId)
			.query(UUID.class)
			.single()).isEqualTo(anaId);

		// Persisted: visible when the task is reopened, also by the assignee.
		Session rui = signIn("rui@matriz.test");
		mvc.perform(get("/api/v1/tasks/{id}", task).with(rui.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.assignees.length()").value(2))
			.andExpect(jsonPath("$.version").value(1));

		unassign(ana, task, ruiId).andExpect(status().isNoContent()).andExpect(matchesContract());
		mvc.perform(get("/api/v1/tasks/{id}", task).with(ana.auth()))
			.andExpect(jsonPath("$.assignees.length()").value(1))
			.andExpect(jsonPath("$.assignees[0].id").value(anaId.toString()));
		unassign(ana, task, anaId).andExpect(status().isNoContent());
		assertThat(assigneeCount(task)).isZero();
		unassign(ana, task, anaId).andExpect(status().isNotFound()).andExpect(matchesContract());
	}

	@Test
	void duplicateAssignmentIsRejected() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Assignee Duplicate");
		UUID task = task(ana, project);
		UUID anaId = userId("ana@matriz.test");

		assign(ana, task, anaId).andExpect(status().isCreated());
		assign(ana, task, anaId).andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("ALREADY_ASSIGNED"));
		assertThat(assigneeCount(task)).isEqualTo(1);
	}

	@Test
	void onlyActiveProjectMembersAreEligible() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Assignee Eligibility");
		UUID task = task(ana, project);

		// Not a member (Marta), an Administrator who is not a member (Vitor), an unknown user.
		for (UUID userId : new UUID[] { userId("marta@matriz.test"), userId("vitor@matriz.test"),
				UUID.randomUUID() }) {
			assign(ana, task, userId).andExpect(status().isUnprocessableContent())
				.andExpect(matchesContract())
				.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_ELIGIBLE"));
		}

		// A deactivated member.
		UUID inactive = UUID.randomUUID();
		jdbc.sql("""
				INSERT INTO users (id, email, name, password_hash, is_active)
				SELECT :id, :email, 'Inactive Person', password_hash, false FROM users WHERE email = 'joao@matriz.test'
				""").param("id", inactive).param("email", "inactive-" + inactive + "@matriz.test").update();
		jdbc.sql("INSERT INTO project_member (project_id, user_id, role) VALUES (:p, :u, 'MEMBER')")
			.param("p", project)
			.param("u", inactive)
			.update();
		assign(ana, task, inactive).andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_ELIGIBLE"));
		assertThat(assigneeCount(task)).isZero();
	}

	@Test
	void requestIsValidated() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID task = task(ana, project(ana, "Assignee Validation"));

		for (String json : new String[] { "{}", "{\"userId\":null}", "{\"userId\":\"not-a-uuid\"}" }) {
			mvc.perform(post("/api/v1/tasks/{id}/assignees", task).with(ana.auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content(json)).andExpect(status().isBadRequest());
		}
		mvc.perform(post("/api/v1/tasks/{id}/assignees", task).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}"))
			.andExpect(jsonPath("$.errors[0].field").value("userId"));
	}

	@Test
	void onlyManagersAndAdministratorsAssign() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Assignee Access");
		addMember(project, "rui@matriz.test");
		UUID task = task(ana, project);
		UUID ruiId = userId("rui@matriz.test");

		Session rui = signIn("rui@matriz.test");
		assign(rui, task, ruiId).andExpect(status().isForbidden()).andExpect(matchesContract());
		Session marta = signIn("marta@matriz.test");
		assign(marta, task, ruiId).andExpect(status().isNotFound()).andExpect(matchesContract());
		mvc.perform(post("/api/v1/tasks/{id}/assignees", UUID.randomUUID()).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"" + ruiId + "\"}")).andExpect(status().isNotFound());

		Session vitor = signIn("vitor@matriz.test");
		assign(vitor, task, ruiId).andExpect(status().isCreated()).andExpect(matchesContract());
		unassign(rui, task, ruiId).andExpect(status().isForbidden());
		unassign(marta, task, ruiId).andExpect(status().isNotFound());
		unassign(vitor, task, ruiId).andExpect(status().isNoContent());
	}

	@Test
	void archivedProjectAssigneesAreReadOnly() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Assignee Archived");
		addMember(project, "rui@matriz.test");
		UUID task = task(ana, project);
		UUID ruiId = userId("rui@matriz.test");
		assign(ana, task, ruiId).andExpect(status().isCreated());

		mvc.perform(post("/api/v1/projects/{id}/archive", project).with(ana.auth())).andExpect(status().isOk());
		assign(ana, task, userId("ana@matriz.test")).andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		unassign(ana, task, ruiId).andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		assertThat(assigneeCount(task)).isEqualTo(1);
	}

	@Test
	void removedMemberIsNoLongerAssigned() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Assignee Membership");
		addMember(project, "rui@matriz.test");
		UUID task = task(ana, project);
		UUID ruiId = userId("rui@matriz.test");
		assign(ana, task, ruiId).andExpect(status().isCreated());

		mvc.perform(delete("/api/v1/projects/{p}/members/{u}", project, ruiId).with(ana.auth()))
			.andExpect(status().isNoContent());
		assertThat(assigneeCount(task)).isZero();
	}

	private ResultActions assign(Session session, UUID task, UUID userId) throws Exception {
		return mvc.perform(post("/api/v1/tasks/{id}/assignees", task).with(session.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"userId\":\"" + userId + "\"}"));
	}

	private ResultActions unassign(Session session, UUID task, UUID userId) throws Exception {
		return mvc.perform(delete("/api/v1/tasks/{id}/assignees/{u}", task, userId).with(session.auth()));
	}

	private UUID project(Session session, String name) throws Exception {
		String body = mvc
			.perform(post("/api/v1/projects").with(session.auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private UUID task(Session session, UUID project) throws Exception {
		String body = mvc
			.perform(post("/api/v1/projects/{id}/tasks", project).with(session.auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"title\":\"Assignable\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private void addMember(UUID project, String email) {
		jdbc.sql("INSERT INTO project_member (project_id, user_id, role) VALUES (:p, :u, 'MEMBER')")
			.param("p", project)
			.param("u", userId(email))
			.update();
	}

	private UUID userId(String email) {
		return jdbc.sql("SELECT id FROM users WHERE email = :e").param("e", email).query(UUID.class).single();
	}

	private long assigneeCount(UUID task) {
		return jdbc.sql("SELECT count(*) FROM task_assignee WHERE task_id = :t")
			.param("t", task)
			.query(Long.class)
			.single();
	}

}
