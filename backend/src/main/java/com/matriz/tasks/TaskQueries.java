package com.matriz.tasks;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
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

/**
 * Read side of tasks: DTO projections in SQL with filters, semantic sort and
 * pagination; assignees and creators are fetched in one batch per page.
 * Callers authorize access to the project first.
 */
@Component
class TaskQueries {

	private static final String COLUMNS = """
			SELECT t.id, t.project_id, t.title, t.description, t.status, t.priority, t.created_by_id, t.version,
			       t.created_at, t.updated_at
			FROM task t
			""";

	private static final String ASSIGNED = "SELECT 1 FROM task_assignee a WHERE a.task_id = t.id";

	private final JdbcClient jdbc;

	private final UserDirectory users;

	TaskQueries(JdbcClient jdbc, UserDirectory users) {
		this.jdbc = jdbc;
		this.users = users;
	}

	/** The project of a task, if the task exists. */
	Optional<UUID> projectOf(UUID taskId) {
		return jdbc.sql("SELECT project_id FROM task WHERE id = :id").param("id", taskId).query(UUID.class).optional();
	}

	Optional<TaskView> find(UUID taskId) {
		return jdbc.sql(COLUMNS + " WHERE t.id = :id")
			.param("id", taskId)
			.query(TaskQueries::row)
			.optional()
			.map(row -> toViews(List.of(row)).getFirst());
	}

	/** Tasks of one project; filters combine with AND, values inside one filter with OR. */
	PageResponse<TaskView> list(UUID projectId, TaskFilter filter, Paging paging) {
		StringBuilder where = new StringBuilder(" WHERE t.project_id = :projectId");
		Map<String, Object> params = new HashMap<>();
		params.put("projectId", projectId);
		if (!filter.statuses().isEmpty()) {
			where.append(" AND t.status IN (:statuses)");
			params.put("statuses", filter.statuses().stream().map(Enum::name).toList());
		}
		if (!filter.priorities().isEmpty()) {
			where.append(" AND t.priority IN (:priorities)");
			params.put("priorities", filter.priorities().stream().map(Enum::name).toList());
		}
		if (filter.unassigned()) {
			where.append(" AND NOT EXISTS (" + ASSIGNED + ")");
		}
		else if (filter.assigneeId() != null) {
			where.append(" AND EXISTS (" + ASSIGNED + " AND a.user_id = :assigneeId)");
			params.put("assigneeId", filter.assigneeId());
		}
		long total = jdbc.sql("SELECT count(*) FROM task t" + where).params(params).query(Long.class).single();
		if (total == 0) {
			return PageResponse.of(List.of(), paging, 0);
		}
		params.put("limit", paging.pageSize());
		params.put("offset", paging.offset());
		List<Row> rows = jdbc.sql(COLUMNS + where + " ORDER BY " + filter.sort().orderBy()
				+ " LIMIT :limit OFFSET :offset")
			.params(params)
			.query(TaskQueries::row)
			.list();
		return PageResponse.of(toViews(rows), paging, total);
	}

	private List<TaskView> toViews(List<Row> rows) {
		if (rows.isEmpty()) {
			return List.of();
		}
		Map<UUID, List<UUID>> assigneeIds = new HashMap<>();
		jdbc.sql("SELECT task_id, user_id FROM task_assignee WHERE task_id IN (:ids)")
			.param("ids", rows.stream().map(Row::id).toList())
			.query(rs -> {
				assigneeIds.computeIfAbsent(rs.getObject("task_id", UUID.class), k -> new ArrayList<>())
					.add(rs.getObject("user_id", UUID.class));
			});
		Set<UUID> userIds = new HashSet<>();
		rows.forEach(r -> userIds.add(r.createdById()));
		assigneeIds.values().forEach(userIds::addAll);
		Map<UUID, UserContact> contacts = users.contacts(userIds);
		return rows.stream().map(row -> {
			List<TaskView.UserSummary> assignees = assigneeIds.getOrDefault(row.id(), List.of())
				.stream()
				.map(id -> summary(id, contacts))
				.sorted(Comparator.comparing((TaskView.UserSummary u) -> u.name().toLowerCase(Locale.ROOT))
					.thenComparing(TaskView.UserSummary::id))
				.toList();
			return new TaskView(row.id(), row.projectId(), row.title(), row.description(), row.status(),
					row.priority(), assignees, summary(row.createdById(), contacts), row.version(), row.createdAt(),
					row.updatedAt());
		}).toList();
	}

	private static TaskView.UserSummary summary(UUID id, Map<UUID, UserContact> contacts) {
		UserContact contact = contacts.get(id);
		return new TaskView.UserSummary(id, contact == null ? "" : contact.name());
	}

	private static Row row(ResultSet rs, int rowNum) throws SQLException {
		return new Row(rs.getObject("id", UUID.class), rs.getObject("project_id", UUID.class), rs.getString("title"),
				rs.getString("description"), TaskStatus.valueOf(rs.getString("status")),
				TaskPriority.valueOf(rs.getString("priority")), rs.getObject("created_by_id", UUID.class),
				rs.getInt("version"), instant(rs, "created_at"), instant(rs, "updated_at"));
	}

	private static Instant instant(ResultSet rs, String column) throws SQLException {
		OffsetDateTime value = rs.getObject(column, OffsetDateTime.class);
		return value == null ? null : value.toInstant();
	}

	private record Row(UUID id, UUID projectId, String title, String description, TaskStatus status,
			TaskPriority priority, UUID createdById, int version, Instant createdAt, Instant updatedAt) {
	}

}
