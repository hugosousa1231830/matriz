package com.matriz.projects;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.identity.UserContact;
import com.matriz.identity.UserDirectory;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;
import com.matriz.shared.Text;

/**
 * Read side of projects: DTO projections in SQL, restricted to the projects
 * the caller can see (member projects; every project for Administrators),
 * with owner names and task counts fetched in one batch per page.
 */
@Component
class ProjectQueries {

	private static final String COLUMNS = """
			SELECT p.id, p.name, p.description, p.status, p.owner_id, p.archived_at, p.created_at, p.updated_at,
			       m.role AS my_role,
			       (SELECT count(*) FROM project_member c WHERE c.project_id = p.id) AS member_count
			""";

	private static final String FROM = """
			FROM project p
			LEFT JOIN project_member m ON m.project_id = p.id AND m.user_id = :userId
			WHERE (:admin OR m.user_id IS NOT NULL)
			""";

	private final JdbcClient jdbc;

	private final UserDirectory users;

	private final TaskCountsProvider taskCounts;

	ProjectQueries(JdbcClient jdbc, UserDirectory users, TaskCountsProvider taskCounts) {
		this.jdbc = jdbc;
		this.users = users;
		this.taskCounts = taskCounts;
	}

	/** Accessible projects sorted by name (case-insensitive), optionally filtered. */
	PageResponse<ProjectView> list(AuthenticatedUser user, String search, boolean includeArchived, Paging paging) {
		StringBuilder filter = new StringBuilder();
		if (!includeArchived) {
			filter.append(" AND p.status = 'ACTIVE'");
		}
		if (search != null) {
			filter.append(" AND lower(p.name) LIKE :pattern ESCAPE '\\'");
		}
		long total = bind(jdbc.sql("SELECT count(*) " + FROM + filter), user, search).query(Long.class).single();
		List<Row> rows = total == 0 ? List.of()
				: bind(jdbc.sql(COLUMNS + FROM + filter + " ORDER BY lower(p.name), p.id LIMIT :limit OFFSET :offset"),
						user, search)
					.param("limit", paging.pageSize())
					.param("offset", paging.offset())
					.query(ProjectQueries::row)
					.list();
		return PageResponse.of(toViews(rows), paging, total);
	}

	/** One project, if the caller can see it. */
	Optional<ProjectView> find(UUID projectId, AuthenticatedUser user) {
		return bind(jdbc.sql(COLUMNS + FROM + " AND p.id = :projectId"), user, null).param("projectId", projectId)
			.query(ProjectQueries::row)
			.optional()
			.map(row -> toViews(List.of(row)).getFirst());
	}

	/** Task counts over all tasks of the caller's accessible active projects. */
	TaskCounts activeTotals(AuthenticatedUser user) {
		List<UUID> ids = bind(jdbc.sql("SELECT p.id " + FROM + " AND p.status = 'ACTIVE'"), user, null)
			.query(UUID.class)
			.list();
		return ids.isEmpty() ? TaskCounts.NONE : taskCounts.totals(ids);
	}

	private static JdbcClient.StatementSpec bind(JdbcClient.StatementSpec spec, AuthenticatedUser user,
			String search) {
		spec = spec.param("userId", user.id()).param("admin", user.admin());
		return search == null ? spec : spec.param("pattern", Text.containsPattern(search));
	}

	private List<ProjectView> toViews(List<Row> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<UUID, UserContact> owners = users.contacts(rows.stream().map(Row::ownerId).distinct().toList());
		Map<UUID, TaskProgress> progress = taskCounts.progressByProject(rows.stream().map(Row::id).toList());
		return rows.stream().map(row -> {
			TaskProgress p = progress.getOrDefault(row.id(), TaskProgress.NONE);
			UserContact owner = owners.get(row.ownerId());
			return new ProjectView(row.id(), row.name(), row.description(), row.status(),
					new ProjectView.Owner(row.ownerId(), owner == null ? "" : owner.name()), row.myRole(),
					row.memberCount(), p.counts(), p.percentDone(), row.archivedAt(), row.createdAt(), row.updatedAt());
		}).toList();
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		String role = rs.getString("my_role");
		return new Row(rs.getObject("id", UUID.class), rs.getString("name"), rs.getString("description"),
				ProjectStatus.valueOf(rs.getString("status")), rs.getObject("owner_id", UUID.class),
				role == null ? null : ProjectRole.valueOf(role), rs.getInt("member_count"),
				instant(rs, "archived_at"), instant(rs, "created_at"), instant(rs, "updated_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private record Row(UUID id, String name, String description, ProjectStatus status, UUID ownerId,
			ProjectRole myRole, int memberCount, Instant archivedAt, Instant createdAt, Instant updatedAt) {
	}

}
