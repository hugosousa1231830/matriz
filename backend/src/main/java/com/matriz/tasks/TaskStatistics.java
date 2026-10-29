package com.matriz.tasks;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import com.matriz.projects.TaskCounts;
import com.matriz.projects.TaskCountsProvider;
import com.matriz.projects.TaskProgress;

/** Task counts per status and % Done, aggregated in SQL over all tasks (not a page). */
@Component
class TaskStatistics implements TaskCountsProvider {

	private static final String COUNTS = """
			count(*) FILTER (WHERE t.status = 'TODO') AS todo,
			count(*) FILTER (WHERE t.status = 'IN_PROGRESS') AS in_progress,
			count(*) FILTER (WHERE t.status = 'DONE') AS done,
			count(*) AS total
			""";

	/** Integer division rounds down; a project without tasks has no row (and 0%). */
	private static final String PERCENT_DONE = "(100 * count(*) FILTER (WHERE t.status = 'DONE') / count(*))::int AS percent_done";

	private final JdbcClient jdbc;

	TaskStatistics(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public Map<UUID, TaskProgress> progressByProject(Collection<UUID> projectIds) {
		Map<UUID, TaskProgress> result = new HashMap<>();
		if (projectIds.isEmpty()) {
			return result;
		}
		jdbc.sql("SELECT t.project_id, " + COUNTS + ", " + PERCENT_DONE
				+ " FROM task t WHERE t.project_id IN (:ids) GROUP BY t.project_id")
			.param("ids", projectIds)
			.query(rs -> {
				var counts = new TaskCounts(rs.getLong("todo"), rs.getLong("in_progress"), rs.getLong("done"),
						rs.getLong("total"));
				result.put(rs.getObject("project_id", UUID.class), new TaskProgress(counts, rs.getInt("percent_done")));
			});
		return result;
	}

	@Override
	public TaskCounts totals(Collection<UUID> projectIds) {
		if (projectIds.isEmpty()) {
			return TaskCounts.NONE;
		}
		return jdbc.sql("SELECT " + COUNTS + " FROM task t WHERE t.project_id IN (:ids)")
			.param("ids", projectIds)
			.query((rs, n) -> new TaskCounts(rs.getLong("todo"), rs.getLong("in_progress"), rs.getLong("done"),
					rs.getLong("total")))
			.single();
	}

}
