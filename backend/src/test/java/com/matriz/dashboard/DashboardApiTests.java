package com.matriz.dashboard;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.matriz.ApiIntegrationTest;

/** {@code GET /dashboard} with the demo data (counts computed in SQL over all tasks). */
class DashboardApiTests extends ApiIntegrationTest {

	@Test
	void showsActiveProjectsWithCountsAndProgress() throws Exception {
		// Other tests add projects managed by Ana; keep Website Redesign on the page.
		mvc.perform(get("/api/v1/dashboard").param("pageSize", "100").with(signIn("ana@matriz.test").auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.user.email").value("ana@matriz.test"))
			.andExpect(jsonPath("$.user.isAdmin").value(false))
			.andExpect(jsonPath("$.projects.items[?(@.name == 'Website Redesign')].taskCounts.todo").value(3))
			.andExpect(jsonPath("$.projects.items[?(@.name == 'Website Redesign')].taskCounts.inProgress").value(1))
			.andExpect(jsonPath("$.projects.items[?(@.name == 'Website Redesign')].taskCounts.done").value(1))
			.andExpect(jsonPath("$.projects.items[?(@.name == 'Website Redesign')].progressPercent").value(20));
	}

	@Test
	void totalsCoverAllAccessibleActiveProjectsAndExcludeArchivedOnes() throws Exception {
		// Marta: Mobile App Launch (active, 3 tasks) and Legacy Intranet (archived, 1 DONE task).
		mvc.perform(get("/api/v1/dashboard").param("pageSize", "1").with(signIn("marta@matriz.test").auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.totals.todo").value(2))
			.andExpect(jsonPath("$.totals.inProgress").value(1))
			.andExpect(jsonPath("$.totals.done").value(0))
			.andExpect(jsonPath("$.totals.total").value(3))
			.andExpect(jsonPath("$.projects.totalItems").value(1))
			.andExpect(jsonPath("$.projects.items[*].name").value(not(hasItem("Legacy Intranet"))));
	}

	@Test
	void administratorsSeeEveryActiveProject() throws Exception {
		mvc.perform(get("/api/v1/dashboard").param("pageSize", "100").with(signIn("vitor@matriz.test").auth()))
			.andExpect(status().isOk())
			.andExpect(matchesContract())
			.andExpect(jsonPath("$.user.isAdmin").value(true))
			.andExpect(jsonPath("$.projects.items[*].name").value(hasItem("Mobile App Launch")))
			.andExpect(jsonPath("$.projects.items[*].name").value(hasItem("Website Redesign")))
			.andExpect(jsonPath("$.projects.items[*].name").value(not(hasItem("Legacy Intranet"))));
	}

	@Test
	void rejectsInvalidPagingAndAnonymousCallers() throws Exception {
		mvc.perform(get("/api/v1/dashboard").param("pageSize", "0").with(signIn("ana@matriz.test").auth()))
			.andExpect(status().isBadRequest());
		mvc.perform(get("/api/v1/dashboard")).andExpect(status().isUnauthorized());
	}

	@Test
	void editsOfStatusAndPriorityKeepTaskFiltersAndCountsConsistent() throws Exception {
		Session ana = signIn("ana@matriz.test");
		String name = "Counts " + UUID.randomUUID();
		UUID project = id(created(ana, post("/api/v1/projects"), "{\"name\":\"" + name + "\"}"));
		UUID first = id(created(ana, post("/api/v1/projects/{p}/tasks", project), "{\"title\":\"First\"}"));
		created(ana, post("/api/v1/projects/{p}/tasks", project), "{\"title\":\"Second\"}");
		created(ana, post("/api/v1/projects/{p}/tasks", project), "{\"title\":\"Third\",\"priority\":\"LOW\"}");

		mvc.perform(json(patch("/api/v1/tasks/{t}", first), "{\"status\":\"DONE\",\"priority\":\"HIGH\",\"version\":1}")
			.with(ana.auth()))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("DONE"))
			.andExpect(jsonPath("$.priority").value("HIGH"))
			.andExpect(jsonPath("$.version").value(2));

		mvc.perform(get("/api/v1/tasks/{t}", first).with(ana.auth()))
			.andExpect(jsonPath("$.status").value("DONE"))
			.andExpect(jsonPath("$.priority").value("HIGH"));
		mvc.perform(get("/api/v1/projects/{p}/tasks", project).param("status", "DONE").with(ana.auth()))
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].title").value("First"));
		mvc.perform(get("/api/v1/projects/{p}/tasks", project).param("status", "TODO").with(ana.auth()))
			.andExpect(jsonPath("$.totalItems").value(2));
		mvc.perform(get("/api/v1/projects/{p}/tasks", project).param("priority", "MEDIUM").with(ana.auth()))
			.andExpect(jsonPath("$.totalItems").value(1))
			.andExpect(jsonPath("$.items[0].title").value("Second"));
		mvc.perform(get("/api/v1/projects/{p}", project).with(ana.auth()))
			.andExpect(jsonPath("$.taskCounts.todo").value(2))
			.andExpect(jsonPath("$.taskCounts.done").value(1))
			.andExpect(jsonPath("$.taskCounts.total").value(3))
			.andExpect(jsonPath("$.progressPercent").value(33));
		String entry = "$.projects.items[?(@.name == '" + name + "')]";
		mvc.perform(get("/api/v1/dashboard").param("pageSize", "100").with(ana.auth()))
			.andExpect(matchesContract())
			.andExpect(jsonPath(entry + ".taskCounts.todo").value(2))
			.andExpect(jsonPath(entry + ".taskCounts.inProgress").value(0))
			.andExpect(jsonPath(entry + ".taskCounts.done").value(1))
			.andExpect(jsonPath(entry + ".progressPercent").value(33));
	}

	private static MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
		return request.contentType(MediaType.APPLICATION_JSON).content(body);
	}

	private String created(Session session, MockHttpServletRequestBuilder request, String body) throws Exception {
		return mvc.perform(json(request, body).with(session.auth()))
			.andExpect(status().isCreated())
			.andReturn()
			.getResponse()
			.getContentAsString();
	}

	private static UUID id(String body) {
		return UUID.fromString(JsonPath.read(body, "$.id"));
	}

}
