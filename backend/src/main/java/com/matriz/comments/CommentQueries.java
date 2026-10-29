package com.matriz.comments;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.matriz.identity.UserContact;
import com.matriz.identity.UserDirectory;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;

/** Read side of comments (oldest first); authors are fetched in one batch per page. */
@Component
class CommentQueries {

	private static final String COLUMNS = "SELECT id, task_id, author_id, body, created_at FROM comment";

	private final JdbcClient jdbc;

	private final UserDirectory users;

	CommentQueries(JdbcClient jdbc, UserDirectory users) {
		this.jdbc = jdbc;
		this.users = users;
	}

	PageResponse<CommentView> list(UUID taskId, Paging paging) {
		long total = jdbc.sql("SELECT count(*) FROM comment WHERE task_id = :taskId")
			.param("taskId", taskId)
			.query(Long.class)
			.single();
		if (total == 0) {
			return PageResponse.of(List.of(), paging, 0);
		}
		List<Row> rows = jdbc.sql(COLUMNS + " WHERE task_id = :taskId ORDER BY created_at, id"
				+ " LIMIT :limit OFFSET :offset")
			.param("taskId", taskId)
			.param("limit", paging.pageSize())
			.param("offset", paging.offset())
			.query(CommentQueries::row)
			.list();
		return PageResponse.of(toViews(rows), paging, total);
	}

	Optional<CommentView> find(UUID id) {
		return jdbc.sql(COLUMNS + " WHERE id = :id")
			.param("id", id)
			.query(CommentQueries::row)
			.optional()
			.map(row -> toViews(List.of(row)).getFirst());
	}

	private List<CommentView> toViews(List<Row> rows) {
		Set<UUID> authorIds = new HashSet<>();
		rows.forEach(row -> authorIds.add(row.authorId()));
		Map<UUID, UserContact> contacts = users.contacts(authorIds);
		return rows.stream().map(row -> {
			UserContact author = contacts.get(row.authorId());
			return new CommentView(row.id(), row.taskId(),
					new CommentView.Author(row.authorId(), author == null ? "" : author.name()), row.body(),
					row.createdAt().toInstant());
		}).toList();
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		return new Row(rs.getObject("id", UUID.class), rs.getObject("task_id", UUID.class),
				rs.getObject("author_id", UUID.class), rs.getString("body"),
				rs.getObject("created_at", OffsetDateTime.class));
	}

	private record Row(UUID id, UUID taskId, UUID authorId, String body, OffsetDateTime createdAt) {
	}

}
