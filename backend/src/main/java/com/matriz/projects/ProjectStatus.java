package com.matriz.projects;

/** Lifecycle of a project: archived projects are read-only and never deleted (ADR-0007). */
public enum ProjectStatus {

	ACTIVE, ARCHIVED

}
