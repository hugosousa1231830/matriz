package com.matriz.dashboard;

import java.util.UUID;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.projects.ProjectService;
import com.matriz.projects.ProjectView;
import com.matriz.projects.TaskCounts;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;

/** {@code GET /dashboard}: accessible active projects with task counts and % Done computed in SQL. */
@RestController
class DashboardController {

	private final ProjectService projects;

	DashboardController(ProjectService projects) {
		this.projects = projects;
	}

	@GetMapping("/api/v1/dashboard")
	Dashboard dashboard(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
		Paging paging = Paging.of(page, pageSize);
		return new Dashboard(new CurrentUser(user.id(), user.name(), user.email(), user.admin()),
				projects.activeTotals(user), projects.list(user, null, false, paging));
	}

	/** {@code Dashboard} in openapi.yaml. */
	record Dashboard(CurrentUser user, TaskCounts totals, PageResponse<ProjectView> projects) {
	}

	/** {@code CurrentUser} in openapi.yaml. */
	record CurrentUser(UUID id, String name, String email, boolean isAdmin) {
	}

}
