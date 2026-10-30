package com.matriz.comments;

import jakarta.validation.constraints.NotBlank;

import org.hibernate.validator.constraints.CodePointLength;

import com.matriz.shared.Text;

/**
 * {@code CommentCreateRequest}: the body is trimmed, then must have 1–2000
 * characters, counted as Unicode code points like PostgreSQL's
 * {@code char_length} (whitespace-only is rejected). The author and date are never
 * taken from the request; any other property gives {@code 400}.
 */
record CommentCreateRequest(@NotBlank(message = "Comment is required.") @CodePointLength(max = CommentCreateRequest.BODY_MAX,
		message = "Comment must have at most 2000 characters.") String body) {

	static final int BODY_MAX = 2000;

	CommentCreateRequest {
		body = Text.trim(body);
	}

}
