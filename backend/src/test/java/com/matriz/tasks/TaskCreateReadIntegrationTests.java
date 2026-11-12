package com.matriz.tasks;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.ResultMatcher;

import com.atlassian.oai.validator.OpenApiInteractionValidator;
import com.atlassian.oai.validator.model.Request;
import com.atlassian.oai.validator.model.SimpleResponse;
import com.atlassian.oai.validator.report.ValidationReport;
import com.jayway.jsonpath.JsonPath;
import com.matriz.ApiIntegrationTest;
import com.matriz.ContractTests;

/**
 * Integration test of the task create → read flow (test plan CR-01 to CR-03,
 * TV-04, AU-01 to AU-03): real HTTP stack (MockMvc with the real sign-in, CSRF
 * and session), real PostgreSQL 16 (Testcontainers) and responses checked
 * against openapi.yaml.
 * <p>
 * Isolation: the database is a throw-away container, never the development
 * one. Each test creates its own project with a unique name, so it does not
 * depend on data or order of other tests. Requests are committed by the
 * application (MockMvc does not share a test transaction), so a rollback is
 * not possible; instead every project created here is deleted in
 * {@link #removeCreatedData()} and the task and project counts are checked
 * to be back to their value before the test.
 */
class TaskCreateReadIntegrationTests extends ApiIntegrationTest {

	private static final String ANA = "ana@matriz.test";

	private static final OpenApiInteractionValidator RESPONSE_VALIDATOR = OpenApiInteractionValidator
		.createForSpecificationUrl(ContractTests.CONTRACT.toUri().toString())
		.build();

	private final List<UUID> createdProjects = new ArrayList<>();

	private long tasksBefore;

	private long projectsBefore;

	@BeforeEach
	void recordInitialState() {
		tasksBefore = countAll("task");
		projectsBefore = countAll("project");
	}

	@AfterEach
	void removeCreatedData() {
		for (UUID project : createdProjects) {
			jdbc.sql("DELETE FROM task WHERE project_id = :p").param("p", project).update();
			jdbc.sql("DELETE FROM project_member WHERE project_id = :p").param("p", project).update();
			jdbc.sql("DELETE FROM project WHERE id = :p").param("p", project).update();
		}
		assertThat(countAll("task")).as("tasks after cleanup").isEqualTo(tasksBefore);
		assertThat(countAll("project")).as("projects after cleanup").isEqualTo(projectsBefore);
	}

	@Test
	void managerCreatesTaskAndReadsItBackFromApiAndDatabase() throws Exception {
		Session ana = signIn(ANA);
		UUID project = newProject(ana);
		String title = "Integration task " + UUID.randomUUID().toString().substring(0, 8);

		MvcResult created = createTask(ana, project, "{\"title\":\"" + title + "\"}")
			.andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
			.andExpect(jsonPath("$.projectId").value(project.toString()))
			.andExpect(jsonPath("$.title").value(title))
			.andExpect(jsonPath("$.description").value(nullValue()))
			.andExpect(jsonPath("$.status").value("TODO"))
			.andExpect(jsonPath("$.priority").value("MEDIUM"))
			.andExpect(jsonPath("$.assignees").isEmpty())
			.andExpect(jsonPath("$.createdBy.id").value(userId(ANA).toString()))
			.andExpect(jsonPath("$.createdBy.name").value("Ana Costa"))
			.andExpect(jsonPath("$.version").value(1))
			.andReturn();
		String createdBody = created.getResponse().getContentAsString();
		UUID id = idOf(createdBody);
		String location = created.getResponse().getHeader("Location");
		assertThat(location).endsWith("/api/v1/tasks/" + id);

		// Read it back through the URL returned in Location: same task, same values.
		String readBody = mvc.perform(get(location.substring(location.indexOf("/api/v1/"))).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andReturn()
			.getResponse()
			.getContentAsString();
		Map<String, Object> createdTask = JsonPath.read(createdBody, "$");
		Map<String, Object> readTask = JsonPath.read(readBody, "$");
		assertThat(readTask).isEqualTo(createdTask);

		// Persisted in PostgreSQL with the values taken from the session, not the client.
		Map<String, Object> row = jdbc
			.sql("SELECT project_id, title, description, status, priority, created_by_id, version"
					+ " FROM task WHERE id = :id")
			.param("id", id)
			.query()
			.singleRow();
		assertThat(row).containsEntry("project_id", project)
			.containsEntry("title", title)
			.containsEntry("description", null)
			.containsEntry("status", "TODO")
			.containsEntry("priority", "MEDIUM")
			.containsEntry("created_by_id", userId(ANA))
			.containsEntry("version", 1);
		assertThat(count("SELECT count(*) FROM task_assignee WHERE task_id = :id", id)).isZero();
		assertThat(countAll("task")).isEqualTo(tasksBefore + 1);

		// CR-02: listed in the project and counted as to do.
		mvc.perform(get("/api/v1/projects/{id}/tasks", project).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(id.toString()))
			.andExpect(jsonPath("$.items[0].title").value(title));
		mvc.perform(get("/api/v1/projects/{id}", project).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.taskCounts.todo").value(1))
			.andExpect(jsonPath("$.taskCounts.total").value(1));
	}

	@Test
	void titleAtMinimumIsTrimmedBeforeItIsStored() throws Exception {
		Session ana = signIn(ANA);
		UUID project = newProject(ana);

		UUID id = idOf(createTask(ana, project, "{\"title\":\"  abc  \"}").andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.title").value("abc"))
			.andReturn()
			.getResponse()
			.getContentAsString());

		mvc.perform(get("/api/v1/tasks/{id}", id).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.title").value("abc"));
		assertThat(jdbc.sql("SELECT title FROM task WHERE id = :id").param("id", id).query(String.class).single())
			.isEqualTo("abc");
	}

	@Test
	void invalidTitleIsRejectedAsProblemAndNothingIsStored() throws Exception {
		Session ana = signIn(ANA);
		UUID project = newProject(ana);

		for (String invalid : List.of("{\"title\":\"   \"}", "{\"title\":\" ab \"}", "{}")) {
			createTask(ana, project, invalid).andExpect(status().isBadRequest())
				.andExpect(responseMatchesContract())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.status").value(400))
				.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
				.andExpect(jsonPath("$.errors[0].field").value("title"));
		}

		assertThat(count("SELECT count(*) FROM task WHERE project_id = :id", project)).isZero();
		assertThat(countAll("task")).isEqualTo(tasksBefore);
		mvc.perform(get("/api/v1/projects/{id}/tasks", project).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.totalItems").value(0));
	}

	@Test
	void nonexistentTaskOrProjectAnswersNotFoundAndStoresNothing() throws Exception {
		Session ana = signIn(ANA);

		mvc.perform(get("/api/v1/tasks/{id}", UUID.randomUUID()).with(ana.auth()))
			.andExpect(status().isNotFound())
			.andExpect(matchesContract())
			.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		createTask(ana, UUID.randomUUID(), "{\"title\":\"Orphan task\"}").andExpect(status().isNotFound())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(countAll("task")).isEqualTo(tasksBefore);
	}

	@Test
	void anonymousCallerGetsUnauthenticatedOnTaskEndpointsAndChangesNothing() throws Exception {
		Session ana = signIn(ANA);
		UUID project = newProject(ana);
		UUID task = idOf(createTask(ana, project, "{\"title\":\"Existing task\"}").andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString());
		// A valid CSRF token without a session: the request fails on authentication, not on CSRF.
		Cookie xsrf = csrfCookie();

		List<ResultActions> calls = List.of(
				mvc.perform(get("/api/v1/projects/{id}/tasks", project)),
				mvc.perform(get("/api/v1/tasks/{id}", task)),
				mvc.perform(post("/api/v1/projects/{id}/tasks", project).cookie(xsrf)
					.header("X-XSRF-TOKEN", xsrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"title\":\"Anonymous task\"}")),
				mvc.perform(patch("/api/v1/tasks/{id}", task).cookie(xsrf)
					.header("X-XSRF-TOKEN", xsrf.getValue())
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"title\":\"Anonymous edit\",\"version\":1}")),
				mvc.perform(delete("/api/v1/tasks/{id}", task).cookie(xsrf).header("X-XSRF-TOKEN", xsrf.getValue())));
		for (ResultActions call : calls) {
			call.andExpect(status().isUnauthorized())
				.andExpect(matchesContract())
				.andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
				.andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
		}

		assertThat(count("SELECT count(*) FROM task WHERE project_id = :id", project)).isEqualTo(1);
		assertThat(jdbc.sql("SELECT title || '/' || version FROM task WHERE id = :id")
			.param("id", task)
			.query(String.class)
			.single()).isEqualTo("Existing task/1");
	}

	@Test
	void createAndReadFollowTheProjectRoles() throws Exception {
		Session ana = signIn(ANA);
		UUID project = newProject(ana);
		jdbc.sql("INSERT INTO project_member (project_id, user_id, role) VALUES (:p, :u, 'MEMBER')")
			.param("p", project)
			.param("u", userId("rui@matriz.test"))
			.update();
		UUID task = idOf(createTask(ana, project, "{\"title\":\"Visible to members\"}")
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString());
		Session rui = signIn("rui@matriz.test");
		Session marta = signIn("marta@matriz.test");

		// AU-03: a Member reads the task but cannot create one.
		mvc.perform(get("/api/v1/tasks/{id}", task).with(rui.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.title").value("Visible to members"));
		createTask(rui, project, "{\"title\":\"Member task\"}").andExpect(status().isForbidden())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("FORBIDDEN"));

		// AU-02: an outsider using the real identifiers gets 404, not 403.
		mvc.perform(get("/api/v1/tasks/{id}", task).with(marta.auth()))
			.andExpect(status().isNotFound())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mvc.perform(get("/api/v1/projects/{id}/tasks", project).with(marta.auth()))
			.andExpect(status().isNotFound())
			.andExpect(matchesContract());
		createTask(marta, project, "{\"title\":\"Outsider task\"}").andExpect(status().isNotFound())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		assertThat(jdbc.sql("SELECT title FROM task WHERE project_id = :id").param("id", project).query(String.class)
			.list()).containsExactly("Visible to members");
	}

	/**
	 * Checks only the response against openapi.yaml: {@link #matchesContract()}
	 * also validates the request, which negative cases break on purpose.
	 */
	private static ResultMatcher responseMatchesContract() {
		return result -> {
			MockHttpServletResponse response = result.getResponse();
			SimpleResponse.Builder actual = SimpleResponse.Builder.status(response.getStatus())
				.withBody(response.getContentAsString())
				.withContentType(response.getContentType());
			ValidationReport report = RESPONSE_VALIDATOR.validateResponse(result.getRequest().getRequestURI(),
					Request.Method.valueOf(result.getRequest().getMethod()), actual.build());
			assertThat(report.getMessages()).as("response contract violations").isEmpty();
		};
	}

	private UUID newProject(Session session) throws Exception {
		String name = "IT project " + UUID.randomUUID().toString().substring(0, 8);
		String body = mvc
			.perform(post("/api/v1/projects").with(session.auth())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"name\":\"" + name + "\"}"))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID id = idOf(body);
		createdProjects.add(id);
		return id;
	}

	private ResultActions createTask(Session session, UUID project, String json) throws Exception {
		return mvc.perform(post("/api/v1/projects/{id}/tasks", project).with(session.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private static UUID idOf(String body) {
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private UUID userId(String email) {
		return jdbc.sql("SELECT id FROM users WHERE email = :e").param("e", email).query(UUID.class).single();
	}

	private long countAll(String table) {
		return jdbc.sql("SELECT count(*) FROM " + table).query(Long.class).single();
	}

	private long count(String sql, UUID id) {
		return jdbc.sql(sql).param("id", id).query(Long.class).single();
	}

}
