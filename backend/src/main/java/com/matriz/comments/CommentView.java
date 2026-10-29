package com.matriz.comments;

import java.time.Instant;
import java.util.UUID;

/** A comment as returned by the API ({@code Comment} in openapi.yaml). */
record CommentView(UUID id, UUID taskId, Author author, String body, Instant createdAt) {

	/** {@code UserSummary} in openapi.yaml. */
	record Author(UUID id, String name) {
	}

}
