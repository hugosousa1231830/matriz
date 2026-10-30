package com.matriz.projects;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.matriz.ApiIntegrationTest;

/** Project CRUD, search, pagination and archive over HTTP, checked against openapi.yaml. */
class ProjectApiTests extends ApiIntegrationTest {

	@Test
	void createdProjectIsOwnedByTheCallerAndPersisted() throws Exception {
		Session joao = signIn("joao@matriz.test");

		String body = createProject(joao, "{\"name\":\"  Garden Plan  \",\"description\":\"  Spring beds.  \"}")
			.andExpect(status().isCreated())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.name").value("Garden Plan"))
			.andExpect(jsonPath("$.description").value("Spring beds."))
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.owner.name").value("João Ferreira"))
			.andExpect(jsonPath("$.myRole").value("MANAGER"))
			.andExpect(jsonPath("$.memberCount").value(1))
			.andExpect(jsonPath("$.taskCounts.total").value(0))
			.andExpect(jsonPath("$.progressPercent").value(0))
			.andExpect(jsonPath("$.archivedAt").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
		UUID joaoId = userId("joao@matriz.test");

		assertThat(jdbc.sql("SELECT owner_id FROM project WHERE id = :id").param("id", id).query(UUID.class).single())
			.isEqualTo(joaoId);
		assertThat(jdbc.sql("SELECT role FROM project_member WHERE project_id = :id AND user_id = :u")
			.param("id", id)
			.param("u", joaoId)
			.query(String.class)
			.single()).isEqualTo("MANAGER");
		mvc.perform(get("/api/v1/projects/{id}", id).with(joao.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.name").value("Garden Plan"));
	}

	@Test
	void createReturnsLocationAndRejectsClientChosenOwner() throws Exception {
		Session joao = signIn("joao@matriz.test");

		String body = createProject(joao, "{\"name\":\"Kitchen Remodel\"}").andExpect(status().isCreated())
			.andExpect(header().string("Location", containsString("/api/v1/projects/")))
			.andExpect(jsonPath("$.description").value(nullValue()))
			.andReturn()
			.getResponse()
			.getContentAsString();
		String id = JsonPath.read(body, "$.id");
		createProject(joao, "{\"name\":\"Kitchen Remodel 2\",\"ownerId\":\"" + UUID.randomUUID() + "\"}")
			.andExpect(status().isBadRequest());
		assertThat(jdbc.sql("SELECT count(*) FROM project WHERE name = 'Kitchen Remodel 2'").query(Long.class).single())
			.isZero();
		assertThat(id).isNotBlank();
	}

	@Test
	void nameAndDescriptionAreValidated() throws Exception {
		Session joao = signIn("joao@matriz.test");

		createProject(joao, "{\"name\":\"   \"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		createProject(joao, "{\"name\":\" ab \"}").andExpect(status().isBadRequest());
		createProject(joao, "{\"name\":\"" + "x".repeat(101) + "\"}").andExpect(status().isBadRequest());
		createProject(joao, "{}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].message").value("Name is required."));
		createProject(joao, "{\"name\":\"Valid name\",\"description\":\"" + "d".repeat(1001) + "\"}")
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("description"));
		createProject(joao, "{\"name\":\"" + "y".repeat(100) + "\",\"description\":\"" + "d".repeat(1000) + "\"}")
			.andExpect(status().isCreated());
		createProject(joao, "not json").andExpect(status().isBadRequest());
	}

	@Test
	void lengthsCountCharactersNotUtf16Units() throws Exception {
		Session joao = signIn("joao@matriz.test");
		String emoji = "\uD83D\uDE00";

		createProject(joao, "{\"name\":\"" + emoji.repeat(2) + "\"}").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		createProject(joao, "{\"name\":\"" + emoji.repeat(100) + "\",\"description\":\"" + emoji.repeat(1000) + "\"}")
			.andExpect(status().isCreated());
		UUID id = create(joao, "Emoji target");
		mvc.perform(patch("/api/v1/projects/{id}", id).with(joao.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + emoji.repeat(2) + "\"}"))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("name"));
		mvc.perform(patch("/api/v1/projects/{id}", id).with(joao.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"" + emoji.repeat(100) + "\"}")).andExpect(status().isOk());
	}

	@Test
	void managersEditMembersCannotOutsidersDoNotSeeIt() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID id = create(ana, "Edit Target");
		addMember(id, "rui@matriz.test", "MEMBER");

		mvc.perform(patch("/api/v1/projects/{id}", id).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"  Edited Target \",\"description\":\"New text\"}"))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.name").value("Edited Target"))
			.andExpect(jsonPath("$.description").value("New text"));
		mvc.perform(patch("/api/v1/projects/{id}", id).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"description\":null}"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.name").value("Edited Target"))
			.andExpect(jsonPath("$.description").value(nullValue()));
		mvc.perform(patch("/api/v1/projects/{id}", id).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{}")).andExpect(status().isBadRequest());
		mvc.perform(patch("/api/v1/projects/{id}", id).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":null}")).andExpect(status().isBadRequest());
		mvc.perform(patch("/api/v1/projects/{id}", id).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"  \"}")).andExpect(status().isBadRequest());

		mvc.perform(patch("/api/v1/projects/{id}", id).with(signIn("rui@matriz.test").auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Hijack\"}"))
			.andExpect(status().isForbidden())
			.andExpect(matchesContract());
		mvc.perform(patch("/api/v1/projects/{id}", id).with(signIn("marta@matriz.test").auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Hijack\"}"))
			.andExpect(status().isNotFound())
			.andExpect(matchesContract());
		mvc.perform(get("/api/v1/projects/{id}", id).with(signIn("marta@matriz.test").auth()))
			.andExpect(status().isNotFound());
		mvc.perform(get("/api/v1/projects/{id}", UUID.randomUUID()).with(ana.auth()))
			.andExpect(status().isNotFound());
	}

	@Test
	void listShowsOnlyAccessibleProjectsSortedByName() throws Exception {
		Session marta = signIn("marta@matriz.test");

		mvc.perform(get("/api/v1/projects").with(marta.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.items[*].name").value(hasItem("Mobile App Launch")))
			.andExpect(jsonPath("$.items[*].name").value(not(hasItem("Website Redesign"))))
			.andExpect(jsonPath("$.items[*].name").value(not(hasItem("Legacy Intranet"))))
			.andExpect(jsonPath("$.page").value(1))
			.andExpect(jsonPath("$.pageSize").value(20));
		mvc.perform(get("/api/v1/projects").param("includeArchived", "true").with(marta.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.items[*].name").value(hasItem("Legacy Intranet")));

		mvc.perform(get("/api/v1/projects").param("search", "website").with(signIn("vitor@matriz.test").auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items[0].name").value("Website Redesign"))
			.andExpect(jsonPath("$.items[0].myRole").value(nullValue()));
	}

	@Test
	void searchIsCaseInsensitiveAndPaginated() throws Exception {
		Session joao = signIn("joao@matriz.test");
		String tag = "Zq" + UUID.randomUUID().toString().substring(0, 6);
		create(joao, tag + " charlie");
		create(joao, tag + " Alpha");
		create(joao, tag + " bravo");
		create(joao, "Other " + tag.toLowerCase() + " 100%_x");

		String body = mvc.perform(get("/api/v1/projects").param("search", "  " + tag.toUpperCase() + " ")
			.param("pageSize", "2")
			.with(joao.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.totalItems").value(4))
			.andExpect(jsonPath("$.pageSize").value(2))
			.andReturn()
			.getResponse()
			.getContentAsString();
		List<String> first = JsonPath.read(body, "$.items[*].name");
		assertThat(first).containsExactly("Other " + tag.toLowerCase() + " 100%_x", tag + " Alpha");
		mvc.perform(get("/api/v1/projects").param("search", tag).param("pageSize", "2").param("page", "2")
			.with(joao.auth()))
			.andExpect(jsonPath("$.items[0].name").value(tag + " bravo"))
			.andExpect(jsonPath("$.items[1].name").value(tag + " charlie"));
		mvc.perform(get("/api/v1/projects").param("search", tag).param("page", "9").with(joao.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.items").isEmpty())
			.andExpect(jsonPath("$.totalItems").value(4));
		// LIKE wildcards are matched literally.
		mvc.perform(get("/api/v1/projects").param("search", "100%_").with(joao.auth()))
			.andExpect(jsonPath("$.totalItems").value(1));
		mvc.perform(get("/api/v1/projects").param("search", "%").with(joao.auth()))
			.andExpect(jsonPath("$.items[*].name").value(hasItem("Other " + tag.toLowerCase() + " 100%_x")));
	}

	@Test
	void invalidListParametersAreRejected() throws Exception {
		Session joao = signIn("joao@matriz.test");

		mvc.perform(get("/api/v1/projects").param("pageSize", "101").with(joao.auth()))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.errors[0].field").value("pageSize"));
		mvc.perform(get("/api/v1/projects").param("page", "0").with(joao.auth())).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/projects").param("page", "x").with(joao.auth())).andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/projects").param("search", "s".repeat(101)).with(joao.auth()))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/projects")).andExpect(status().isUnauthorized());
	}

	@Test
	void archivedProjectsAreReadOnlyAndCanBeUnarchived() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID id = create(ana, "Archive Me");
		addMember(id, "rui@matriz.test", "MEMBER");

		mvc.perform(post("/api/v1/projects/{id}/archive", id).with(signIn("rui@matriz.test").auth()))
			.andExpect(status().isForbidden());
		String archived = mvc.perform(post("/api/v1/projects/{id}/archive", id).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.status").value("ARCHIVED"))
			.andExpect(jsonPath("$.archivedAt").isNotEmpty())
			.andReturn()
			.getResponse()
			.getContentAsString();
		String archivedAt = JsonPath.read(archived, "$.archivedAt");

		mvc.perform(post("/api/v1/projects/{id}/archive", id).with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.archivedAt").value(archivedAt));
		mvc.perform(patch("/api/v1/projects/{id}", id).with(ana.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content("{\"name\":\"Renamed\"}"))
			.andExpect(status().isConflict())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		mvc.perform(get("/api/v1/projects").param("search", "Archive Me").with(ana.auth()))
			.andExpect(jsonPath("$.totalItems").value(0));
		mvc.perform(get("/api/v1/projects/{id}", id).with(ana.auth())).andExpect(status().isOk());

		mvc.perform(post("/api/v1/projects/{id}/unarchive", id).with(signIn("vitor@matriz.test").auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.status").value("ACTIVE"))
			.andExpect(jsonPath("$.archivedAt").value(nullValue()))
			.andExpect(jsonPath("$.myRole").value(nullValue()));
		mvc.perform(post("/api/v1/projects/{id}/unarchive", id).with(ana.auth())).andExpect(status().isOk());
		Instant stored = jdbc.sql("SELECT updated_at FROM project WHERE id = :id")
			.param("id", id)
			.query(OffsetDateTime.class)
			.single()
			.toInstant();
		assertThat(stored).isAfterOrEqualTo(Instant.parse(archivedAt));
	}

	@Test
	void projectsCannotBeDeleted() throws Exception {
		Session ana = signIn("ana@matriz.test");
		UUID id = create(ana, "Not Deletable");

		mvc.perform(org.springframework.test.web.servlet.request.MockMvcRequestBuilders
			.delete("/api/v1/projects/{id}", id)
			.with(ana.auth())).andExpect(status().isMethodNotAllowed());
	}

	private ResultActions createProject(Session session, String json) throws Exception {
		return mvc.perform(post("/api/v1/projects").with(session.auth())
			.contentType(MediaType.APPLICATION_JSON)
			.content(json));
	}

	private UUID create(Session session, String name) throws Exception {
		String body = createProject(session, "{\"name\":\"" + name + "\"}").andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

	private void addMember(UUID project, String email, String role) {
		jdbc.sql("INSERT INTO project_member (project_id, user_id, role) VALUES (:p, :u, :r)")
			.param("p", project)
			.param("u", userId(email))
			.param("r", role)
			.update();
	}

	private UUID userId(String email) {
		return jdbc.sql("SELECT id FROM users WHERE email = :e").param("e", email).query(UUID.class).single();
	}

}
