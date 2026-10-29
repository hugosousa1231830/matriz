package com.matriz.projects;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Import;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.ApiIntegrationTest;
import com.matriz.identity.AuthenticatedUser;

/**
 * {@link ProjectAccessPolicy} over HTTP with real sessions and the demo data:
 * the identity comes from the session only, and decisions map to
 * {@code 404}/{@code 403}/{@code 409} problem responses.
 */
@Import(ProjectAccessPolicyTests.ProbeController.class)
class ProjectAccessPolicyTests extends ApiIntegrationTest {

	private UUID website;

	private UUID mobile;

	private UUID intranet;

	private UUID mockups;

	private UUID siteMap;

	private UUID storeListing;

	@BeforeEach
	void loadIds() {
		website = project("Website Redesign");
		mobile = project("Mobile App Launch");
		intranet = project("Legacy Intranet");
		mockups = task("Design homepage mockups");
		siteMap = task("Define site map");
		storeListing = task("Prepare app store listing");
	}

	@Test
	void managerMayManageTheirProject() throws Exception {
		Session ana = signIn("ana@matriz.test");

		expect(ana, website, ProjectAction.EDIT_PROJECT, 204);
		expect(ana, website, ProjectAction.MANAGE_MEMBERS, 204);
		expectTask(ana, website, siteMap, ProjectAction.DELETE_TASK, 204);
	}

	@Test
	void memberMayViewAndCommentButNotManage() throws Exception {
		Session rui = signIn("rui@matriz.test");

		expect(rui, website, ProjectAction.VIEW, 204);
		expect(rui, website, ProjectAction.COMMENT, 204);
		expect(rui, website, ProjectAction.EDIT_PROJECT, 403).andExpect(jsonPath("$.code").value("FORBIDDEN"));
		expect(rui, website, ProjectAction.CREATE_TASK, 403);
		expect(rui, website, ProjectAction.MANAGE_MEMBERS, 403);
	}

	@Test
	void memberMayEditOnlyTasksAssignedToThem() throws Exception {
		Session rui = signIn("rui@matriz.test");

		expectTask(rui, website, mockups, ProjectAction.EDIT_TASK, 204);
		expectTask(rui, website, siteMap, ProjectAction.EDIT_TASK, 403);
		expectTask(rui, website, mockups, ProjectAction.ASSIGN, 403);
	}

	@Test
	void nonMembersCannotSeeTheProject() throws Exception {
		Session marta = signIn("marta@matriz.test");
		Session joao = signIn("joao@matriz.test");

		expect(marta, website, ProjectAction.VIEW, 404).andExpect(jsonPath("$.code").value("NOT_FOUND"));
		expect(marta, website, ProjectAction.EDIT_PROJECT, 404);
		expect(joao, mobile, ProjectAction.VIEW, 404);
		expect(marta, mobile, ProjectAction.VIEW, 204);
	}

	@Test
	void unknownProjectsAndTasksOfOtherProjectsAreNotFound() throws Exception {
		Session ana = signIn("ana@matriz.test");

		expect(ana, UUID.randomUUID(), ProjectAction.VIEW, 404);
		expectTask(ana, website, storeListing, ProjectAction.VIEW, 404);
		expectTask(ana, website, UUID.randomUUID(), ProjectAction.VIEW, 404);
	}

	@Test
	void administratorSeesAndManagesEveryProject() throws Exception {
		Session vitor = signIn("vitor@matriz.test");

		expect(vitor, website, ProjectAction.EDIT_PROJECT, 204);
		expect(vitor, mobile, ProjectAction.MANAGE_MEMBERS, 204);
		expectTask(vitor, mobile, storeListing, ProjectAction.EDIT_TASK, 204);
		expect(vitor, intranet, ProjectAction.VIEW, 204);
	}

	@Test
	void archivedProjectIsReadOnlyExceptUnarchive() throws Exception {
		Session marta = signIn("marta@matriz.test");
		Session vitor = signIn("vitor@matriz.test");

		expect(marta, intranet, ProjectAction.VIEW, 204);
		expect(marta, intranet, ProjectAction.EDIT_PROJECT, 409)
			.andExpect(jsonPath("$.code").value("PROJECT_ARCHIVED"));
		expect(marta, intranet, ProjectAction.CREATE_TASK, 409);
		expect(marta, intranet, ProjectAction.COMMENT, 409);
		expect(marta, intranet, ProjectAction.UNARCHIVE, 204);
		expect(vitor, intranet, ProjectAction.EDIT_PROJECT, 409);
	}

	@Test
	void probeRequiresASession() throws Exception {
		mvc.perform(get("/api/v1/test-probe/projects/{p}/{a}", website, ProjectAction.VIEW))
			.andExpect(status().isUnauthorized());
	}

	private org.springframework.test.web.servlet.ResultActions expect(Session session, UUID project,
			ProjectAction action, int status) throws Exception {
		return mvc.perform(get("/api/v1/test-probe/projects/{p}/{a}", project, action).with(session.auth()))
			.andExpect(status().is(status));
	}

	private void expectTask(Session session, UUID project, UUID task, ProjectAction action, int status)
			throws Exception {
		mvc.perform(get("/api/v1/test-probe/projects/{p}/tasks/{t}/{a}", project, task, action).with(session.auth()))
			.andExpect(status().is(status));
	}

	private UUID project(String name) {
		return jdbc.sql("SELECT id FROM project WHERE name = :n").param("n", name).query(UUID.class).single();
	}

	private UUID task(String title) {
		return jdbc.sql("SELECT id FROM task WHERE title = :t").param("t", title).query(UUID.class).single();
	}

	/** Test-only endpoints that run the policy with the session's user. */
	@RestController
	static class ProbeController {

		private final ProjectAccessPolicy policy;

		ProbeController(ProjectAccessPolicy policy) {
			this.policy = policy;
		}

		@GetMapping("/api/v1/test-probe/projects/{projectId}/{action}")
		ResponseEntity<Void> project(@PathVariable UUID projectId, @PathVariable ProjectAction action,
				@AuthenticationPrincipal AuthenticatedUser user) {
			policy.require(projectId, user, action);
			return ResponseEntity.noContent().build();
		}

		@GetMapping("/api/v1/test-probe/projects/{projectId}/tasks/{taskId}/{action}")
		ResponseEntity<Void> task(@PathVariable UUID projectId, @PathVariable UUID taskId,
				@PathVariable ProjectAction action, @AuthenticationPrincipal AuthenticatedUser user) {
			policy.requireTask(projectId, taskId, user, action);
			return ResponseEntity.noContent().build();
		}

	}

}
