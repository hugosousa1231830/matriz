package com.matriz.projects;

/**
 * Task counts per status over all tasks of one or more projects (ADR-0015).
 */
public record TaskCounts(long todo, long inProgress, long done, long total) {

	public static final TaskCounts NONE = new TaskCounts(0, 0, 0, 0);

}
