package com.matriz;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * Verification case 08: direct API calls with identifiers of resources the
 * caller may not see or change are refused, and nothing is changed. Ana
 * (Manager) owns a project with Rui (Member) assigned to one task; Marta is a
 * Manager of another project and an outsider here.
 */
class ApiAuthorizationTests extends ApiIntegrationTest {

	private Session ana;

	private UUID project;

	private UUID ruisTask;

	private UUID anasTask;

	private UUID ruiId;

	private UUID anaId;

	@BeforeEach
	void createProjectWithTasksAndComments() throws Exception {
		ana = signIn("ana@matriz.test");
		anaId = userId("ana@matriz.test");
		ruiId = userId("rui@matriz.test");
		project = id(send(ana, post("/api/v1/projects"), "{\"name\":\"Private Plans " + UUID.randomUUID() + "\"}"));
		send(ana, post("/api/v1/projects/{p}/members", project), "{\"userId\":\"" + ruiId + "\"}");
		ruisTask = id(send(ana, post("/api/v1/projects/{p}/tasks", project),
				"{\"title\":\"Rui's task\",\"assigneeIds\":[\"" + ruiId + "\"]}"));
		anasTask = id(send(ana, post("/api/v1/projects/{p}/tasks", project), "{\"title\":\"Ana's task\"}"));
		send(ana, post("/api/v1/tasks/{t}/comments", ruisTask), "{\"body\":\"Kick-off notes\"}");
	}

	@Test
	void outsiderGetsNotFoundForEveryOperationAndChangesNothing() throws Exception {
		Session marta = signIn("marta@matriz.test");
		List<String> before = state();

		List<MockHttpServletRequestBuilder> requests = List.of(
				get("/api/v1/projects/{p}", project),
				json(patch("/api/v1/projects/{p}", project), "{\"name\":\"Taken over\"}"),
				post("/api/v1/projects/{p}/archive", project),
				post("/api/v1/projects/{p}/unarchive", project),
				get("/api/v1/projects/{p}/members", project),
				json(post("/api/v1/projects/{p}/members", project),
						"{\"userId\":\"" + userId("marta@matriz.test") + "\",\"role\":\"MANAGER\"}"),
				json(patch("/api/v1/projects/{p}/members/{u}", project, ruiId), "{\"role\":\"MANAGER\"}"),
				delete("/api/v1/projects/{p}/members/{u}", project, ruiId),
				get("/api/v1/projects/{p}/member-candidates", project).param("search", "joao"),
				get("/api/v1/projects/{p}/tasks", project),
				json(post("/api/v1/projects/{p}/tasks", project), "{\"title\":\"Injected\"}"),
				get("/api/v1/tasks/{t}", ruisTask),
				json(patch("/api/v1/tasks/{t}", ruisTask), "{\"status\":\"DONE\",\"version\":1}"),
				delete("/api/v1/tasks/{t}", anasTask),
				json(post("/api/v1/tasks/{t}/assignees", anasTask), "{\"userId\":\"" + ruiId + "\"}"),
				delete("/api/v1/tasks/{t}/assignees/{u}", ruisTask, ruiId),
				get("/api/v1/tasks/{t}/comments", ruisTask),
				json(post("/api/v1/tasks/{t}/comments", ruisTask), "{\"body\":\"Not my project\"}"));
		for (MockHttpServletRequestBuilder request : requests) {
			mvc.perform(request.with(marta.auth()))
				.andExpect(status().isNotFound())
				.andExpect(matchesContract())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		}

		String name = JsonPath.read(mvc.perform(get("/api/v1/projects/{p}", project).with(ana.auth()))
			.andReturn()
			.getResponse()
			.getContentAsString(), "$.name");
		mvc.perform(get("/api/v1/projects").param("search", name).param("includeArchived", "true").with(marta.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(0));
		mvc.perform(get("/api/v1/dashboard").param("pageSize", "100").with(marta.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.projects.items[*].id").value(not(hasItem(project.toString()))));
		assertThat(state()).isEqualTo(before);
	}

	@Test
	void memberCannotPerformManagerActionsAndChangesNothing() throws Exception {
		Session rui = signIn("rui@matriz.test");
		List<String> before = state();

		List<MockHttpServletRequestBuilder> requests = List.of(
				json(patch("/api/v1/projects/{p}", project), "{\"name\":\"Renamed by Rui\"}"),
				post("/api/v1/projects/{p}/archive", project),
				json(post("/api/v1/projects/{p}/members", project),
						"{\"userId\":\"" + userId("joao@matriz.test") + "\"}"),
				json(patch("/api/v1/projects/{p}/members/{u}", project, ruiId), "{\"role\":\"MANAGER\"}"),
				delete("/api/v1/projects/{p}/members/{u}", project, anaId),
				json(post("/api/v1/projects/{p}/tasks", project), "{\"title\":\"Not allowed\"}"),
				json(patch("/api/v1/tasks/{t}", anasTask), "{\"status\":\"DONE\",\"version\":1}"),
				delete("/api/v1/tasks/{t}", ruisTask),
				json(post("/api/v1/tasks/{t}/assignees", anasTask), "{\"userId\":\"" + ruiId + "\"}"),
				delete("/api/v1/tasks/{t}/assignees/{u}", ruisTask, ruiId));
		for (MockHttpServletRequestBuilder request : requests) {
			mvc.perform(request.with(rui.auth()))
				.andExpect(status().isForbidden())
				.andExpect(matchesContract())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		}
		assertThat(state()).isEqualTo(before);

		// What Rui may do: read everything in the project and edit the task assigned to him.
		mvc.perform(get("/api/v1/tasks/{t}", anasTask).with(rui.auth())).andExpect(status().isOk());
		mvc.perform(json(patch("/api/v1/tasks/{t}", ruisTask), "{\"status\":\"IN_PROGRESS\",\"version\":1}")
			.with(rui.auth())).andExpect(status().isOk());
	}

	@Test
	void identityAndOwnershipCannotBeSuppliedByTheClient() throws Exception {
		Session marta = signIn("marta@matriz.test");
		UUID mobile = jdbc.sql("SELECT id FROM project WHERE name = 'Mobile App Launch'").query(UUID.class).single();

		// Marta manages Mobile App Launch, but users of other projects are not eligible there.
		mvc.perform(json(post("/api/v1/projects/{p}/tasks", mobile), "{\"title\":\"Borrow Rui\",\"assigneeIds\":[\""
				+ ruiId + "\"]}").with(marta.auth()))
			.andExpect(status().isUnprocessableContent())
			.andExpect(jsonPath("$.code").value("ASSIGNEE_NOT_ELIGIBLE"));
		// A task cannot be moved into another project, and authorship cannot be claimed.
		mvc.perform(json(patch("/api/v1/tasks/{t}", anasTask), "{\"projectId\":\"" + mobile + "\",\"version\":1}")
			.with(ana.auth())).andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/v1/tasks/{t}/comments", ruisTask), "{\"body\":\"Hi\",\"authorId\":\"" + ruiId
				+ "\"}").with(ana.auth())).andExpect(status().isBadRequest());
		mvc.perform(json(post("/api/v1/projects"), "{\"name\":\"Someone else's\",\"ownerId\":\"" + ruiId + "\"}")
			.with(ana.auth())).andExpect(status().isBadRequest());
		assertThat(jdbc.sql("SELECT project_id FROM task WHERE id = :t").param("t", anasTask).query(UUID.class).single())
			.isEqualTo(project);
	}

	/** Everything the requests above could change, as comparable text. */
	private List<String> state() {
		return jdbc.sql("""
				SELECT 'project ' || p.name || ' ' || p.status || ' ' || p.owner_id || ' ' || p.updated_at
				  FROM project p WHERE p.id = :p
				UNION ALL SELECT 'member ' || m.user_id || ' ' || m.role FROM project_member m WHERE m.project_id = :p
				UNION ALL SELECT 'task ' || t.id || ' ' || t.title || ' ' || t.status || ' ' || t.version
				  FROM task t WHERE t.project_id = :p
				UNION ALL SELECT 'assignee ' || a.task_id || ' ' || a.user_id
				  FROM task_assignee a JOIN task t ON t.id = a.task_id WHERE t.project_id = :p
				UNION ALL SELECT 'comment ' || c.id || ' ' || c.body
				  FROM comment c JOIN task t ON t.id = c.task_id WHERE t.project_id = :p
				ORDER BY 1
				""").param("p", project).query(String.class).list();
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private String send(Session session, MockHttpServletRequestBuilder request, String body) throws Exception {
		return mvc.perform(json(request, body).with(session.auth()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static UUID id(String body) {
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private UUID userId(String email) {
		return jdbc.sql("SELECT id FROM users WHERE email = :e").param("e", email).query(UUID.class).single();
	}

}
