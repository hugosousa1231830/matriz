package com.matriz.comments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import com.matriz.shared.Text;

/**
 * {@code CommentCreateRequest}: the body is trimmed, then must have 1–2000
 * characters (whitespace-only is rejected). The author and date are never
 * taken from the request; any other property gives {@code 400}.
 */
record CommentCreateRequest(@NotBlank(message = "Comment is required.") @Size(max = CommentCreateRequest.BODY_MAX,
		message = "Comment must have at most 2000 characters.") String body) {

	static final int BODY_MAX = 2000;

	CommentCreateRequest {
		body = Text.trim(body);
	}

}
