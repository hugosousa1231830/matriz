package com.matriz.projects;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.identity.UserContact;
import com.matriz.shared.ApiException;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;
import com.matriz.shared.Text;

/** Projects and their members ({@code Projects} and {@code Members} tags in openapi.yaml). */
@RestController
@RequestMapping("/api/v1/projects")
class ProjectController {

	private static final int SEARCH_MAX = 100;

	private static final int CANDIDATE_SEARCH_MIN = 2;

	private final ProjectService service;

	ProjectController(ProjectService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<ProjectView> list(@AuthenticationPrincipal AuthenticatedUser user,
			@RequestParam(required = false) String search,
			@RequestParam(defaultValue = "false") boolean includeArchived,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
		String term = Text.trimToNull(search);
		if (term != null && term.length() > SEARCH_MAX) {
			throw ApiException.validation("search", "Search must have at most 100 characters.");
		}
		return service.list(user, term, includeArchived, Paging.of(page, pageSize));
	}

	@PostMapping
	ResponseEntity<ProjectView> create(@AuthenticationPrincipal AuthenticatedUser user,
			@Valid @RequestBody ProjectCreateRequest body) {
		ProjectView project = service.create(user, body.name(), body.description());
		return ResponseEntity.created(URI.create("/api/v1/projects/" + project.id())).body(project);
	}

	@GetMapping("/{projectId}")
	ProjectView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
		return service.get(user, projectId);
	}

	@PatchMapping("/{projectId}")
	ProjectView update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@RequestBody(required = false) ProjectUpdateRequest body) {
		ProjectService.ProjectChanges changes = ProjectRules.validate(body);
		return service.update(user, projectId, changes);
	}

	@PostMapping("/{projectId}/archive")
	ProjectView archive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
		return service.archive(user, projectId);
	}

	@PostMapping("/{projectId}/unarchive")
	ProjectView unarchive(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId) {
		return service.unarchive(user, projectId);
	}

	@GetMapping("/{projectId}/members")
	List<ProjectService.MemberView> members(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID projectId) {
		return service.members(user, projectId);
	}

	@PostMapping("/{projectId}/members")
	@ResponseStatus(HttpStatus.CREATED)
	ProjectService.MemberView addMember(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID projectId, @Valid @RequestBody MemberAddRequest body) {
		return service.addMember(user, projectId, body.userId(), body.role() == null ? ProjectRole.MEMBER
				: body.role());
	}

	@PatchMapping("/{projectId}/members/{userId}")
	ProjectService.MemberView changeRole(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID projectId, @PathVariable UUID userId, @Valid @RequestBody MemberRoleUpdateRequest body) {
		return service.changeRole(user, projectId, userId, body.role());
	}

	@DeleteMapping("/{projectId}/members/{userId}")
	ResponseEntity<Void> removeMember(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@PathVariable UUID userId) {
		service.removeMember(user, projectId, userId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{projectId}/member-candidates")
	List<UserContact> memberCandidates(@AuthenticationPrincipal AuthenticatedUser user,
			@PathVariable UUID projectId, @RequestParam String search) {
		String term = Text.trim(search);
		if (term.length() < CANDIDATE_SEARCH_MIN || term.length() > SEARCH_MAX) {
			throw ApiException.validation("search", "Search must have between 2 and 100 characters.");
		}
		return service.memberCandidates(user, projectId, term);
	}

	/** {@code MemberAddRequest}; {@code role} defaults to {@code MEMBER}. */
	record MemberAddRequest(@NotNull(message = "User is required.") UUID userId, ProjectRole role) {
	}

	/** {@code MemberRoleUpdateRequest}. */
	record MemberRoleUpdateRequest(@NotNull(message = "Role is required.") ProjectRole role) {
	}

}
