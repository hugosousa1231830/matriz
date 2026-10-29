package com.matriz.projects;

import static com.matriz.projects.ProjectAccess.Decision.ALLOWED;
import static com.matriz.projects.ProjectAccess.Decision.ARCHIVED;
import static com.matriz.projects.ProjectAccess.Decision.FORBIDDEN;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import com.matriz.shared.ApiException;
import com.matriz.shared.ProblemCode;

/** The access matrix of the implementation plan (§4), action by action. */
class ProjectAccessTests {

	private static final UUID PROJECT = UUID.randomUUID();

	private static final EnumSet<ProjectAction> MEMBER_ACTIONS = EnumSet.of(ProjectAction.VIEW, ProjectAction.COMMENT);

	private static ProjectAccess manager(boolean archived) {
		return new ProjectAccess(PROJECT, ProjectRole.MANAGER, false, archived);
	}

	private static ProjectAccess member(boolean archived) {
		return new ProjectAccess(PROJECT, ProjectRole.MEMBER, false, archived);
	}

	private static ProjectAccess admin(boolean archived) {
		return new ProjectAccess(PROJECT, null, true, archived);
	}

	@ParameterizedTest
	@EnumSource(ProjectAction.class)
	void managersAndAdministratorsMayDoEverythingOnActiveProjects(ProjectAction action) {
		assertThat(manager(false).decide(action, false)).isEqualTo(ALLOWED);
		assertThat(admin(false).decide(action, false)).isEqualTo(ALLOWED);
	}

	@ParameterizedTest
	@EnumSource(ProjectAction.class)
	void membersMayViewAndCommentOnly(ProjectAction action) {
		assertThat(member(false).decide(action, false))
			.isEqualTo(MEMBER_ACTIONS.contains(action) ? ALLOWED : FORBIDDEN);
	}

	@Test
	void membersMayEditTasksTheyAreAssignedTo() {
		assertThat(member(false).decide(ProjectAction.EDIT_TASK, true)).isEqualTo(ALLOWED);
		assertThat(member(false).decide(ProjectAction.EDIT_TASK, false)).isEqualTo(FORBIDDEN);
		assertThat(member(false).decide(ProjectAction.ASSIGN, true)).isEqualTo(FORBIDDEN);
		assertThat(member(false).decide(ProjectAction.DELETE_TASK, true)).isEqualTo(FORBIDDEN);
	}

	@ParameterizedTest
	@EnumSource(ProjectAction.class)
	void archivedProjectsAreReadOnlyExceptForArchiveAndUnarchive(ProjectAction action) {
		// Archiving an archived project is an idempotent no-op; unarchive is the way back.
		var expected = action == ProjectAction.VIEW || action == ProjectAction.ARCHIVE
				|| action == ProjectAction.UNARCHIVE ? ALLOWED : ARCHIVED;
		assertThat(manager(true).decide(action, false)).isEqualTo(expected);
		assertThat(admin(true).decide(action, false)).isEqualTo(expected);
	}

	@Test
	void permissionIsCheckedBeforeArchivedState() {
		assertThat(member(true).decide(ProjectAction.EDIT_PROJECT, false)).isEqualTo(FORBIDDEN);
		assertThat(member(true).decide(ProjectAction.UNARCHIVE, false)).isEqualTo(FORBIDDEN);
		assertThat(member(true).decide(ProjectAction.COMMENT, false)).isEqualTo(ARCHIVED);
		assertThat(member(true).decide(ProjectAction.EDIT_TASK, true)).isEqualTo(ARCHIVED);
		assertThat(member(true).decide(ProjectAction.VIEW, false)).isEqualTo(ALLOWED);
	}

	@Test
	void requireTranslatesDecisionsToProblemCodes() {
		assertThat(member(false).require(ProjectAction.COMMENT)).isNotNull();
		assertThatThrownBy(() -> member(false).require(ProjectAction.CREATE_TASK)).isInstanceOfSatisfying(
				ApiException.class, e -> assertThat(e.code()).isEqualTo(ProblemCode.FORBIDDEN));
		assertThatThrownBy(() -> manager(true).require(ProjectAction.CREATE_TASK)).isInstanceOfSatisfying(
				ApiException.class, e -> assertThat(e.code()).isEqualTo(ProblemCode.PROJECT_ARCHIVED));
	}

	@Test
	void roleHelpers() {
		assertThat(manager(false).isManager()).isTrue();
		assertThat(admin(false).isManager()).isTrue();
		assertThat(admin(false).isMember()).isFalse();
		assertThat(member(false).isManager()).isFalse();
		assertThat(member(false).isMember()).isTrue();
	}

}
