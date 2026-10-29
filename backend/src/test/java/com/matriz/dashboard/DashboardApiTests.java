package com.matriz.dashboard;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;

import com.matriz.ApiIntegrationTest;

/** {@code GET /dashboard} with the demo data (counts computed in SQL over all tasks). */
class DashboardApiTests extends ApiIntegrationTest {

	@Test
	void showsActiveProjectsWithCountsAndProgress() throws Exception {
		mvc.perform(get("/api/v1/dashboard").with(signIn("ana@matriz.test").auth()))
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

}
