package com.matriz.tasks;

import java.net.URI;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.shared.ApiException;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;

/** Tasks of a project ({@code Tasks} tag in openapi.yaml). Request validation ({@code 400}) comes first. */
@RestController
class TaskController {

	private static final String UNASSIGNED = "none";

	private final TaskService service;

	TaskController(TaskService service) {
		this.service = service;
	}

	@GetMapping("/api/v1/projects/{projectId}/tasks")
	PageResponse<TaskView> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@RequestParam(required = false) List<String> status, @RequestParam(required = false) List<String> priority,
			@RequestParam(required = false) String assigneeId, @RequestParam(required = false) String sort,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
		Set<TaskStatus> statuses = parseEnums("status", status, TaskStatus.class);
		Set<TaskPriority> priorities = parseEnums("priority", priority, TaskPriority.class);
		UUID assignee = null;
		boolean unassigned = false;
		if (assigneeId != null) {
			if (UNASSIGNED.equals(assigneeId)) {
				unassigned = true;
			}
			else {
				assignee = parse("assigneeId", assigneeId, UUID::fromString,
						"Assignee must be a user id or \"none\".");
			}
		}
		TaskSort.Order order = sort == null ? TaskSort.Order.DEFAULT
				: TaskSort.Order.parse(sort)
					.orElseThrow(() -> ApiException.validation("sort",
							"Sort must be one of createdAt, priority, status or title, optionally prefixed with -."));
		Paging paging = Paging.of(page, pageSize);
		return service.list(user, projectId, new TaskFilter(statuses, priorities, assignee, unassigned, order),
				paging);
	}

	@PostMapping("/api/v1/projects/{projectId}/tasks")
	ResponseEntity<TaskView> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID projectId,
			@Valid @RequestBody TaskCreateRequest body) {
		if (new HashSet<>(body.assigneeIds()).size() != body.assigneeIds().size()) {
			throw ApiException.validation("assigneeIds", "Assignees must not be repeated.");
		}
		TaskView task = service.create(user, projectId, body.title(), body.description(), body.priority(),
				body.assigneeIds());
		return ResponseEntity.created(URI.create("/api/v1/tasks/" + task.id())).body(task);
	}

	@GetMapping("/api/v1/tasks/{taskId}")
	TaskView get(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID taskId) {
		return service.get(user, taskId);
	}

	@PatchMapping("/api/v1/tasks/{taskId}")
	TaskView update(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID taskId,
			@RequestBody(required = false) TaskUpdateRequest body) {
		return service.update(user, taskId, TaskRules.validate(body));
	}

	@DeleteMapping("/api/v1/tasks/{taskId}")
	ResponseEntity<Void> delete(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID taskId) {
		service.delete(user, taskId);
		return ResponseEntity.noContent().build();
	}

	/** Comma-separated (or repeated) enum values; blank entries are ignored, unknown ones give {@code 400}. */
	private static <E extends Enum<E>> Set<E> parseEnums(String field, List<String> values, Class<E> type) {
		Set<E> result = EnumSet.noneOf(type);
		if (values == null) {
			return result;
		}
		for (String value : values) {
			String trimmed = value.strip();
			if (!trimmed.isEmpty()) {
				result.add(parse(field, trimmed, v -> Enum.valueOf(type, v), "Unknown " + field + " value."));
			}
		}
		return result;
	}

	private static <T> T parse(String field, String value, Function<String, T> parser, String message) {
		try {
			return parser.apply(value);
		}
		catch (IllegalArgumentException ex) {
			throw ApiException.validation(field, message);
		}
	}

}
