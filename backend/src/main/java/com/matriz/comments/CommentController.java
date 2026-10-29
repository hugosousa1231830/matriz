package com.matriz.comments;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;

/** Comments of a task ({@code Comments} tag in openapi.yaml). Request validation ({@code 400}) comes first. */
@RestController
@RequestMapping("/api/v1/tasks/{taskId}/comments")
class CommentController {

	private final CommentService service;

	CommentController(CommentService service) {
		this.service = service;
	}

	@GetMapping
	PageResponse<CommentView> list(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID taskId,
			@RequestParam(required = false) Integer page, @RequestParam(required = false) Integer pageSize) {
		return service.list(user, taskId, Paging.of(page, pageSize));
	}

	@PostMapping
	ResponseEntity<CommentView> create(@AuthenticationPrincipal AuthenticatedUser user, @PathVariable UUID taskId,
			@Valid @RequestBody CommentCreateRequest body) {
		return ResponseEntity.status(HttpStatus.CREATED).body(service.create(user, taskId, body.body()));
	}

}
