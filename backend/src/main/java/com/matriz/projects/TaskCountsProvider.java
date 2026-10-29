package com.matriz.projects;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Task counts computed in SQL by the module that owns tasks (implemented in
 * {@code tasks}, so this module does not depend on it).
 */
public interface TaskCountsProvider {

	/** Counts and % Done per project; projects without tasks may be missing from the map. */
	Map<UUID, TaskProgress> progressByProject(Collection<UUID> projectIds);

	/** Counts over all tasks of the given projects together. */
	TaskCounts totals(Collection<UUID> projectIds);

}
