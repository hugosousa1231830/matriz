package com.matriz.comments;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.matriz.ApiIntegrationTest;

/**
 * Listing and adding task comments over HTTP, checked against openapi.yaml.
 * Each test works on its own project (Ana = Manager, Rui = Member, Marta =
 * outsider, Vitor = Administrator and not a member).
 */
class CommentApiTests extends ApiIntegrationTest {

	@Test
	void commentGetsAuthorAndDateFromTheServerAndIsPersisted() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Comment Persistence");
		addMember(project, "rui@matriz.test");
		UUID task = task(ana, project);
		Session rui = signIn("rui@matriz.test");

		Instant before = Instant.now();
		String body = comment(rui, task, "{\"body\":\"  Looks good to me.  \"}").andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(header().doesNotExist("Location"))
			.andExpect(jsonPath("$.taskId").value(task.toString()))
			.andExpect(jsonPath("$.body").value("Looks good to me."))
			.andExpect(jsonPath("$.author.id").value(userId("rui@matriz.test").toString()))
			.andExpect(jsonPath("$.author.name").value("Rui Pereira"))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
		Instant createdAt = Instant.parse(JsonPath.read(body, "$.createdAt"));
		assertThat(createdAt).isBetween(before.minusSeconds(1), Instant.now().plusSeconds(1));

		assertThat(jdbc.sql("SELECT body, author_id FROM comment WHERE id = :id AND task_id = :t")
			.param("id", id)
			.param("t", task)
			.query((rs, n) -> rs.getString(1) + "|" + rs.getString(2))
			.single()).isEqualTo("Looks good to me.|" + userId("rui@matriz.test"));

		// Reopening the task shows the same comment, also to other members.
		list(ana, task, "").andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].id").value(id.toString()))
			.andExpect(jsonPath("$.items[0].author.name").value("Rui Pereira"))
			.andExpect(jsonPath("$.items[0].createdAt").value(createdAt.toString()));
	}

	@Test
	void authorAndDateCannotBeSupplied() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID task = task(ana, project(ana, "Comment Author"));
		comment(ana, task, "{\"body\":\"Hi\",\"authorId\":\"" + userId("rui@matriz.test") + "\"}")
			.andExpect(status().isBadRequest());
		comment(ana, task, "{\"body\":\"Hi\",\"createdAt\":\"2020-01-01T00:00:00Z\"}")
			.andExpect(status().isBadRequest());
		assertThat(commentCount(task)).isZero();
	}

	@Test
	void bodyIsValidated() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID task = task(ana, project(ana, "Comment Validation"));

		for (String json : new String[] { "{}", "{\"body\":null}", "{\"body\":\"\"}", "{\"body\":\"   \\n\\t \"}",
				"{\"body\":\"" + "x".repeat(2001) + "\"}" }) {
			comment(ana, task, json).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.errors[0].field").value("body"));
		}
		assertThat(commentCount(task)).isZero();

		// Limits apply after trimming.
		comment(ana, task, "{\"body\":\"" + "x".repeat(2000) + "\"}").andExpect(status().isCreated())
			.andExpect(matchesContract());
		comment(ana, task, "{\"body\":\"  " + "y".repeat(2000) + "  \"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.body").value("y".repeat(2000)));
		comment(ana, task, "{\"body\":\"x\"}").andExpect(status().isCreated());
		assertThat(commentCount(task)).isEqualTo(3);
	}

	@Test
	void commentsAreListedOldestFirstAndPaginated() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID task = task(ana, project(ana, "Comment Order"));
		for (int i = 1; i <= 3; i++) {
			comment(ana, task, "{\"body\":\"Comment " + i + "\"}").andExpect(status().isCreated());
		}

		list(ana, task, "").andExpect(status().isOk())
			.andExpect(jsonPath("$.items[*].body").value(contains("Comment 1", "Comment 2", "Comment 3")))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.pageSize").value(20));
		list(ana, task, "page=2&pageSize=2").andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.items[*].body").value(contains("Comment 3")))
			.andExpect(jsonPath("$.totalItems").value(3));
		list(ana, task, "pageSize=101").andExpect(status().isBadRequest());
		list(ana, task, "page=0").andExpect(status().isBadRequest());
	}

	@Test
	void anyMemberAndAdministratorsComment() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Comment Access");
		addMember(project, "rui@matriz.test");
		UUID task = task(ana, project);

		comment(signIn("rui@matriz.test"), task, "{\"body\":\"Member\"}").andExpect(status().isCreated());
		Session vitor = signIn("vitor@matriz.test");
		comment(vitor, task, "{\"body\":\"Admin\"}").andExpect(status().isCreated())
			.andExpect(jsonPath("$.author.name").value("Vitor Almeida"));
		list(vitor, task, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(2));

		Session marta = signIn("marta@matriz.test");
		comment(marta, task, "{\"body\":\"Outsider\"}").andExpect(status().isNotFound())
			.andExpect(matchesContract());
		list(marta, task, "").andExpect(status().isNotFound()).andExpect(matchesContract());
		comment(ana, UUID.randomUUID(), "{\"body\":\"Nowhere\"}").andExpect(status().isNotFound());
		list(ana, UUID.randomUUID(), "").andExpect(status().isNotFound());
		assertThat(commentCount(task)).isEqualTo(2);
	}

	@Test
	void archivedProjectCommentsAreReadOnly() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID project = project(ana, "Comment Archived");
		UUID task = task(ana, project);
		comment(ana, task, "{\"body\":\"Before archiving\"}").andExpect(status().isCreated());

		mvc.perform(post("/api/v1/projects/{id}/archive", project).with(ana.auth())).andExpect(status().isOk());
		comment(ana, task, "{\"body\":\"After archiving\"}").andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		list(ana, task, "").andExpect(status().isOk()).andExpect(jsonPath("$.totalItems").value(1));
	}

	@Test
	void commentsGoWithTheirTask() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID task = task(ana, project(ana, "Comment Cascade"));
		comment(ana, task, "{\"body\":\"Ephemeral\"}").andExpect(status().isCreated());

		mvc.perform(delete("/api/v1/tasks/{id}", task).with(ana.auth())).andExpect(status().isNoContent());
		assertThat(commentCount(task)).isZero();
	}

	private ResultActions comment(Session session, UUID task, String json) throws Exception {
		return mvc.perform(post("/api/v1/tasks/{id}/comments", task).with(session.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private ResultActions list(Session session, UUID task, String query) throws Exception {
		return mvc.perform(get("/api/v1/tasks/{id}/comments" + (query.isEmpty() ? "" : "?" + query), task)
			.with(session.auth()));
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
				.content("{\"title\":\"Discussed\"}"))
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

	private long commentCount(UUID task) {
		return jdbc.sql("SELECT count(*) FROM comment WHERE task_id = :t").param("t", task).query(Long.class).single();
	}

}
