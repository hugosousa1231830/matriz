package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.matriz.ApiIntegrationTest;

/**
 * Task create, list (filters, sort, pagination), read, edit with optimistic
 * concurrency and delete over HTTP, checked against openapi.yaml. Each test
 * works on its own project (Ana = Manager, Rui = Member, Marta = outsider).
 */
class TaskApiTests extends ApiIntegrationTest {

	@Test
	void createdTaskHasDefaultsAndIsPersisted() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Persistence");

		String body = createTask(ana, project, "{\"title\":\"  Draft agenda  \",\"description\":\"  For Monday.  \"}")
			.andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.title").value("Draft agenda"))
			.andExpect(jsonPath("$.description").value("For Monday."))
			.andExpect(jsonPath("$.status").value("TODO"))
			.andExpect(jsonPath("$.priority").value("MEDIUM"))
			.andExpect(jsonPath("$.assignees").isEmpty())
			.andExpect(jsonPath("$.createdBy.name").value("Ana Costa"))
			.andExpect(jsonPath("$.version").value(1))
			.andExpect(jsonPath("$.projectId").value(project.toString()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(body, "$.id"));

		assertThat(jdbc.sql("SELECT title, status, priority, created_by_id, version FROM task WHERE id = :id")
			.param("id", id)
			.query((rs, n) -> List.of(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
					rs.getString(5)))
			.single()).containsExactly("Draft agenda", "TODO", "MEDIUM", userId("ana@matriz.test").toString(), "1");
		mvc.perform(get("/api/v1/tasks/{id}", id).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.title").value("Draft agenda"));
		mvc.perform(get("/api/v1/projects/{id}", project).with(ana.auth()))
			.andExpect(jsonPath("$.taskCounts.todo").value(1))
			.andExpect(jsonPath("$.taskCounts.total").value(1));
	}

	@Test
	void createReturnsLocationOfTheNewTask() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Location");
		ResultActions result = createTask(ana, project, "{\"title\":\"Located\"}").andExpect(status().isCreated());
		String id = JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
		result.andExpect(header().string("Location", endsWith("/api/v1/tasks/" + id)));
	}

	@Test
	void titleDescriptionAndPriorityAreValidated() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Validation");

		createTask(ana, project, "{\"title\":\"   \"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("title"));
		createTask(ana, project, "{\"title\":\" ab \"}").andExpect(status().isBadRequest());
		createTask(ana, project, "{\"title\":\"" + "x".repeat(151) + "\"}").andExpect(status().isBadRequest());
		createTask(ana, project, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("title"));
		createTask(ana, project, "{\"title\":\"Valid\",\"description\":\"" + "d".repeat(2001) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("description"));
		createTask(ana, project, "{\"title\":\"Valid\",\"priority\":\"URGENT\"}").andExpect(status().isBadRequest());
		createTask(ana, project, "{\"title\":\"Valid\",\"status\":\"DONE\"}").andExpect(status().isBadRequest());
		createTask(ana, project, "{\"title\":\"Valid\",\"createdById\":\"" + UUID.randomUUID() + "\"}")
			.andExpect(status().isBadRequest());
		UUID anaId = userId("ana@matriz.test");
		createTask(ana, project, "{\"title\":\"Valid\",\"assigneeIds\":[\"" + anaId + "\",\"" + anaId + "\"]}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("assigneeIds"));
		assertThat(taskCount(project)).isZero();

		createTask(ana, project,
				"{\"title\":\"" + "y".repeat(150) + "\",\"description\":\"" + "d".repeat(2000)
						+ "\",\"priority\":\"HIGH\"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.priority").value("HIGH"));
		createTask(ana, project, "{\"title\":\"Blank description\",\"description\":\"   \"}")
			.andExpect(status().isCreated())
			.andExpect(jsonPath("$.description").value(nullValue()));
	}

	@Test
	void assigneesAtCreationMustBeMembers() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Assignees");
		addMember(project, "rui@matriz.test");

		createTask(ana, project, "{\"title\":\"Shared work\",\"assigneeIds\":[\"" + userId("rui@matriz.test")
				+ "\",\"" + userId("ana@matriz.test") + "\"]}")
			.andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.assignees[*].name", contains("Ana Costa", "Rui Pereira")));
		createTask(ana, project, "{\"title\":\"Outsider work\",\"assigneeIds\":[\"" + userId("marta@matriz.test")
				+ "\"]}")
			.andExpect(status().isUnprocessableContent())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_ELIGIBLE"));
		createTask(ana, project, "{\"title\":\"Ghost work\",\"assigneeIds\":[\"" + UUID.randomUUID() + "\"]}")
			.andExpect(status().isUnprocessableContent());
		assertThat(taskCount(project)).isEqualTo(1);
	}

	@Test
	void listFiltersSortsAndPaginates() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Listing");
		addMember(project, "rui@matriz.test");
		UUID rui = userId("rui@matriz.test");
		UUID alpha = task(ana, project, "{\"title\":\"Alpha\",\"priority\":\"LOW\"}");
		task(ana, project, "{\"title\":\"bravo\",\"priority\":\"HIGH\",\"assigneeIds\":[\"" + rui + "\"]}");
		UUID charlie = task(ana, project, "{\"title\":\"Charlie\"}");
		setStatus(ana, alpha, "DONE", 1);
		setStatus(ana, charlie, "IN_PROGRESS", 1);

		list(ana, project, "").andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.totalItems").value(3))
			.andExpect(jsonPath("$.items[*].title", contains("Charlie", "bravo", "Alpha")));
		list(ana, project, "status=TODO,IN_PROGRESS").andExpect(matchesContract())
			.andExpect(jsonPath("$.items[*].title", contains("Charlie", "bravo")));
		list(ana, project, "status=DONE").andExpect(matchesContract())
			.andExpect(jsonPath("$.items[*].title", contains("Alpha")));
		list(ana, project, "priority=LOW,HIGH&status=DONE")
			.andExpect(jsonPath("$.items[*].title", contains("Alpha")));
		list(ana, project, "assigneeId=" + rui).andExpect(matchesContract())
			.andExpect(jsonPath("$.items[*].title", contains("bravo")))
			.andExpect(jsonPath("$.items[0].assignees[0].name").value("Rui Pereira"));
		list(ana, project, "assigneeId=none").andExpect(matchesContract())
			.andExpect(jsonPath("$.items[*].title", contains("Charlie", "Alpha")));
		list(ana, project, "sort=title").andExpect(jsonPath("$.items[*].title", contains("Alpha", "bravo", "Charlie")));
		list(ana, project, "sort=-priority")
			.andExpect(jsonPath("$.items[*].title", contains("bravo", "Charlie", "Alpha")));
		list(ana, project, "sort=status")
			.andExpect(jsonPath("$.items[*].title", contains("bravo", "Charlie", "Alpha")));
		list(ana, project, "sort=createdAt")
			.andExpect(jsonPath("$.items[*].title", contains("Alpha", "bravo", "Charlie")));
		list(ana, project, "pageSize=2&page=2").andExpect(matchesContract())
			.andExpect(jsonPath("$.totalItems").value(3))
			.andExpect(jsonPath("$.page").value(2))
			.andExpect(jsonPath("$.items[*].title", contains("Alpha")));

		list(ana, project, "status=LATE").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("status"));
		list(ana, project, "priority=URGENT").andExpect(status().isBadRequest());
		list(ana, project, "assigneeId=nobody").andExpect(status().isBadRequest());
		list(ana, project, "sort=deadline").andExpect(status().isBadRequest());
		list(ana, project, "pageSize=101").andExpect(status().isBadRequest());
		list(signIn("marta@matriz.test"), project, "").andExpect(status().isNotFound()).andExpect(matchesContract());
	}

	@Test
	void permissionsFollowTheAccessMatrix() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Permissions");
		addMember(project, "rui@matriz.test");
		UUID mine = task(ana, project,
				"{\"title\":\"Rui's task\",\"assigneeIds\":[\"" + userId("rui@matriz.test") + "\"]}");
		UUID other = task(ana, project, "{\"title\":\"Ana's task\"}");
		Session rui = signIn("rui@matriz.test");
		Session marta = signIn("marta@matriz.test");

		createTask(rui, project, "{\"title\":\"Not allowed\"}").andExpect(status().isForbidden())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		edit(rui, mine, "{\"title\":\"Rui edited\",\"description\":\"Mine\",\"status\":\"IN_PROGRESS\","
				+ "\"priority\":\"HIGH\",\"version\":1}")
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.title").value("Rui edited"))
			.andExpect(jsonPath("$.version").value(2));
		edit(rui, other, "{\"status\":\"DONE\",\"version\":1}").andExpect(status().isForbidden())
			.andExpect(matchesContract());
		mvc.perform(delete("/api/v1/tasks/{id}", mine).with(rui.auth())).andExpect(status().isForbidden());
		mvc.perform(get("/api/v1/tasks/{id}", other).with(rui.auth())).andExpect(status().isOk());
		list(rui, project, "").andExpect(jsonPath("$.totalItems").value(2));

		mvc.perform(get("/api/v1/tasks/{id}", other).with(marta.auth()))
			.andExpect(status().isNotFound())
			.andExpect(matchesContract());
		edit(marta, other, "{\"status\":\"DONE\",\"version\":1}").andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/tasks/{id}", other).with(marta.auth())).andExpect(status().isNotFound());
		createTask(marta, project, "{\"title\":\"Not visible\"}").andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/tasks/{id}", UUID.randomUUID()).with(ana.auth())).andExpect(status().isNotFound());

		Session vitor = signIn("vitor@matriz.test");
		edit(vitor, other, "{\"priority\":\"LOW\",\"version\":1}").andExpect(status().isOk());
		createTask(vitor, project, "{\"title\":\"By the admin\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.createdBy.name").value("Vitor Almeida"));
		mvc.perform(delete("/api/v1/tasks/{id}", other).with(vitor.auth())).andExpect(status().isNoContent());
	}

	@Test
	void staleVersionIsRejectedAndPartialUpdatesApplyOnlySentFields() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Concurrency");
		UUID id = task(ana, project, "{\"title\":\"Shared doc\",\"description\":\"Text\"}");

		edit(ana, id, "{\"status\":\"IN_PROGRESS\",\"version\":1}").andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.title").value("Shared doc"))
			.andExpect(jsonPath("$.description").value("Text"))
			.andExpect(jsonPath("$.version").value(2));
		edit(ana, id, "{\"title\":\"Lost update\",\"version\":1}").andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("VERSION_CONFLICT"));
		edit(ana, id, "{\"description\":null,\"title\":\"  Renamed  \",\"version\":2}").andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("Renamed"))
			.andExpect(jsonPath("$.description").value(nullValue()))
			.andExpect(jsonPath("$.status").value("IN_PROGRESS"))
			.andExpect(jsonPath("$.version").value(3));

		edit(ana, id, "{\"status\":\"DONE\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("version"));
		edit(ana, id, "{\"version\":3}").andExpect(status().isBadRequest());
		edit(ana, id, "{\"title\":null,\"version\":3}").andExpect(status().isBadRequest());
		edit(ana, id, "{\"title\":\"   \",\"version\":3}").andExpect(status().isBadRequest());
		edit(ana, id, "{\"status\":null,\"version\":3}").andExpect(status().isBadRequest());
		edit(ana, id, "{\"status\":\"LATE\",\"version\":3}").andExpect(status().isBadRequest());
		edit(ana, id, "{\"projectId\":\"" + UUID.randomUUID() + "\",\"version\":3}").andExpect(status().isBadRequest());
		assertThat(jdbc.sql("SELECT version FROM task WHERE id = :id").param("id", id).query(Integer.class).single())
			.isEqualTo(3);
	}

	@Test
	void archivedProjectTasksAreReadOnly() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Archive");
		UUID id = task(ana, project, "{\"title\":\"Frozen\"}");
		mvc.perform(post("/api/v1/projects/{id}/archive", project).with(ana.auth())).andExpect(status().isOk());

		createTask(ana, project, "{\"title\":\"Too late\"}").andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		edit(ana, id, "{\"status\":\"DONE\",\"version\":1}").andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		mvc.perform(delete("/api/v1/tasks/{id}", id).with(ana.auth()))
			.andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		mvc.perform(get("/api/v1/tasks/{id}", id).with(ana.auth())).andExpect(status().isOk());
		list(ana, project, "").andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void deleteRemovesAssignmentsAndCommentsInOneGo() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Delete");
		UUID anaId = userId("ana@matriz.test");
		UUID id = task(ana, project, "{\"title\":\"Disposable\",\"assigneeIds\":[\"" + anaId + "\"]}");
		UUID kept = task(ana, project, "{\"title\":\"Kept\"}");
		jdbc.sql("INSERT INTO comment (id, task_id, author_id, body) VALUES (:id, :task, :author, 'Bye')")
			.param("id", UUID.randomUUID())
			.param("task", id)
			.param("author", anaId)
			.update();

		mvc.perform(delete("/api/v1/tasks/{id}", id).with(ana.auth()))
			.andExpect(status().isNoContent())
			.andExpect(matchesContract());

		assertThat(count("SELECT count(*) FROM task WHERE id = :id", id)).isZero();
		assertThat(count("SELECT count(*) FROM task_assignee WHERE task_id = :id", id)).isZero();
		assertThat(count("SELECT count(*) FROM comment WHERE task_id = :id", id)).isZero();
		assertThat(count("SELECT count(*) FROM task WHERE id = :id", kept)).isEqualTo(1);
		mvc.perform(get("/api/v1/tasks/{id}", id).with(ana.auth())).andExpect(status().isNotFound());
		mvc.perform(delete("/api/v1/tasks/{id}", id).with(ana.auth())).andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/projects/{id}", project).with(ana.auth()))
			.andExpect(jsonPath("$.taskCounts.total").value(1));
	}

	@Test
	void lengthsCountCharactersNotUtf16Units() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Task Emoji Lengths");
		String emoji = "\uD83D\uDE00";

		createTask(ana, project, "{\"title\":\"" + emoji.repeat(2) + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("title"));
		UUID task = task(ana, project, "{\"title\":\"" + emoji.repeat(150) + "\",\"description\":\""
				+ emoji.repeat(2000) + "\"}");
		edit(ana, task, "{\"version\":1,\"title\":\"" + emoji.repeat(2) + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("title"));
		edit(ana, task, "{\"version\":1,\"title\":\"" + emoji.repeat(3) + "\"}").andExpect(status().isOk());
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

	private ResultActions createTask(Session session, UUID project, String json) throws Exception {
		return mvc.perform(post("/api/v1/projects/{id}/tasks", project).with(session.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private UUID task(Session session, UUID project, String json) throws Exception {
		String body = createTask(session, project, json).andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private ResultActions edit(Session session, UUID task, String json) throws Exception {
		return mvc.perform(patch("/api/v1/tasks/{id}", task).with(session.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private void setStatus(Session session, UUID task, String status, int version) throws Exception {
		edit(session, task, "{\"status\":\"" + status + "\",\"version\":" + version + "}")
			.andExpect(status().isOk());
	}

	private ResultActions list(Session session, UUID project, String query) throws Exception {
		return mvc.perform(get("/api/v1/projects/{id}/tasks" + (query.isEmpty() ? "" : "?" + query), project)
			.with(session.auth()));
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

	private long taskCount(UUID project) {
		return count("SELECT count(*) FROM task WHERE project_id = :id", project);
	}

	private long count(String sql, UUID id) {
		return jdbc.sql(sql).param("id", id).query(Long.class).single();
	}

}
