package com.matriz.projects;

/**
 * Task counts of one project and its % Done, both computed in SQL (ADR-0015):
 * {@code floor(100 * done / total)}, {@code 0} without tasks (exceptional case 4).
 */
public record TaskProgress(TaskCounts counts, int percentDone) {

	public static final TaskProgress NONE = new TaskProgress(TaskCounts.NONE, 0);

}
