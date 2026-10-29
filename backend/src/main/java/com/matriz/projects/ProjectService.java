package com.matriz.projects;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.matriz.identity.AuthenticatedUser;
import com.matriz.identity.UserContact;
import com.matriz.identity.UserDirectory;
import com.matriz.shared.ApiException;
import com.matriz.shared.PageResponse;
import com.matriz.shared.Paging;
import com.matriz.shared.ProblemCode;

/**
 * Project and membership use cases. Every operation on an existing project
 * is authorized by {@link ProjectAccessPolicy} with the session's user
 * ({@code 404} → {@code 403} → {@code 409 PROJECT_ARCHIVED}); owner and
 * creator are always taken from the session.
 */
@Service
public class ProjectService {

	/** Maximum number of member candidates returned by a search. */
	static final int MAX_CANDIDATES = 20;

	private final ProjectRepository projects;

	private final ProjectMemberRepository members;

	private final ProjectQueries queries;

	private final ProjectAccessPolicy policy;

	private final UserDirectory users;

	private final ApplicationEventPublisher events;

	private final Clock clock;

	ProjectService(ProjectRepository projects, ProjectMemberRepository members, ProjectQueries queries,
			ProjectAccessPolicy policy, UserDirectory users, ApplicationEventPublisher events, Clock clock) {
		this.projects = projects;
		this.members = members;
		this.queries = queries;
		this.policy = policy;
		this.users = users;
		this.events = events;
		this.clock = clock;
	}

	/** Any signed-in user; the caller becomes the owner and a {@code MANAGER} member (BR11). */
	@Transactional
	public ProjectView create(AuthenticatedUser user, String name, String description) {
		Instant now = now();
		Project project = projects.save(new Project(UUID.randomUUID(), name, description, user.id(), now));
		members.save(new ProjectMember(project.getId(), user.id(), ProjectRole.MANAGER, now));
		projects.flush();
		return view(project.getId(), user);
	}

	/** Accessible projects (all for Administrators), sorted by name; archived ones only on request. */
	@Transactional(readOnly = true)
	public PageResponse<ProjectView> list(AuthenticatedUser user, String search, boolean includeArchived,
			Paging paging) {
		return queries.list(user, search, includeArchived, paging);
	}

	/** Task counts over all accessible active projects (dashboard totals). */
	@Transactional(readOnly = true)
	public TaskCounts activeTotals(AuthenticatedUser user) {
		return queries.activeTotals(user);
	}

	@Transactional(readOnly = true)
	public ProjectView get(AuthenticatedUser user, UUID projectId) {
		policy.require(projectId, user, ProjectAction.VIEW);
		return view(projectId, user);
	}

	/**
	 * Partial update; {@code null} arguments are left unchanged unless the
	 * matching {@code set} flag is true (clearing the description).
	 */
	@Transactional
	public ProjectView update(AuthenticatedUser user, UUID projectId, ProjectChanges changes) {
		policy.require(projectId, user, ProjectAction.EDIT_PROJECT);
		Project project = load(projectId);
		Instant now = now();
		if (changes.nameSet()) {
			project.rename(changes.name(), now);
		}
		if (changes.descriptionSet()) {
			project.describe(changes.description(), now);
		}
		projects.flush();
		return view(projectId, user);
	}

	/** Idempotent: archiving an archived project changes nothing. */
	@Transactional
	public ProjectView archive(AuthenticatedUser user, UUID projectId) {
		policy.require(projectId, user, ProjectAction.ARCHIVE);
		load(projectId).archive(now());
		projects.flush();
		return view(projectId, user);
	}

	/** Allowed on archived projects (with archive); idempotent on active projects. */
	@Transactional
	public ProjectView unarchive(AuthenticatedUser user, UUID projectId) {
		policy.require(projectId, user, ProjectAction.UNARCHIVE);
		load(projectId).unarchive(now());
		projects.flush();
		return view(projectId, user);
	}

	/** Members sorted by name; any member and Administrators. */
	@Transactional(readOnly = true)
	public List<MemberView> members(AuthenticatedUser user, UUID projectId) {
		policy.require(projectId, user, ProjectAction.VIEW);
		Project project = load(projectId);
		List<ProjectMember> list = members.findByIdProjectId(projectId);
		Map<UUID, UserContact> contacts = users.contacts(list.stream().map(ProjectMember::getUserId).toList());
		return list.stream()
			.map(m -> toView(m, contacts.get(m.getUserId()), project))
			.sorted(Comparator.comparing((MemberView m) -> m.user().name().toLowerCase(Locale.ROOT))
				.thenComparing(m -> m.user().id()))
			.toList();
	}

	/** Managers and Administrators add an active user ({@code 422}) who is not yet a member ({@code 409}). */
	@Transactional
	public MemberView addMember(AuthenticatedUser user, UUID projectId, UUID userId, ProjectRole role) {
		policy.require(projectId, user, ProjectAction.MANAGE_MEMBERS);
		Project project = load(projectId);
		UserContact contact = users.findActive(userId)
			.orElseThrow(() -> new ApiException(ProblemCode.USER_NOT_ELIGIBLE));
		if (members.existsById(new ProjectMember.Key(projectId, userId))) {
			throw new ApiException(ProblemCode.ALREADY_MEMBER);
		}
		ProjectMember member = new ProjectMember(projectId, userId, role, now());
		try {
			members.saveAndFlush(member);
		}
		catch (DataIntegrityViolationException ex) {
			// Added concurrently by another request.
			throw new ApiException(ProblemCode.ALREADY_MEMBER);
		}
		return toView(member, contact, project);
	}

	/** The owner cannot be demoted ({@code 422}); setting the owner's role to {@code MANAGER} is a no-op. */
	@Transactional
	public MemberView changeRole(AuthenticatedUser user, UUID projectId, UUID userId, ProjectRole role) {
		policy.require(projectId, user, ProjectAction.MANAGE_MEMBERS);
		Project project = load(projectId);
		ProjectMember member = members.findById(new ProjectMember.Key(projectId, userId))
			.orElseThrow(ApiException::notFound);
		if (project.getOwnerId().equals(userId) && role != ProjectRole.MANAGER) {
			throw new ApiException(ProblemCode.OWNER_IMMUTABLE);
		}
		member.changeRole(role);
		members.flush();
		return toView(member, users.contacts(List.of(userId)).get(userId), project);
	}

	/**
	 * The owner cannot be removed ({@code 422}). The user's assignments on this
	 * project's tasks are deleted in the same transaction; comments are kept.
	 */
	@Transactional
	public void removeMember(AuthenticatedUser user, UUID projectId, UUID userId) {
		policy.require(projectId, user, ProjectAction.MANAGE_MEMBERS);
		Project project = load(projectId);
		ProjectMember member = members.findById(new ProjectMember.Key(projectId, userId))
			.orElseThrow(ApiException::notFound);
		if (project.getOwnerId().equals(userId)) {
			throw new ApiException(ProblemCode.OWNER_IMMUTABLE);
		}
		members.delete(member);
		members.flush();
		events.publishEvent(new ProjectMemberRemoved(projectId, userId));
	}

	/**
	 * Active non-members matching {@code search} on name or email. Managers and
	 * Administrators only; a read, so also allowed on archived projects.
	 */
	@Transactional(readOnly = true)
	public List<UserContact> memberCandidates(AuthenticatedUser user, UUID projectId, String search) {
		ProjectAccess access = policy.access(projectId, user);
		if (!access.isManager()) {
			throw ApiException.forbidden();
		}
		List<UUID> memberIds = members.findByIdProjectId(projectId).stream().map(ProjectMember::getUserId).toList();
		return users.searchActive(search, memberIds, MAX_CANDIDATES);
	}

	private Project load(UUID projectId) {
		return projects.findById(projectId).orElseThrow(ApiException::notFound);
	}

	private ProjectView view(UUID projectId, AuthenticatedUser user) {
		return queries.find(projectId, user).orElseThrow(ApiException::notFound);
	}

	private Instant now() {
		// PostgreSQL stores microseconds; keep responses identical to later reads.
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private static MemberView toView(ProjectMember member, UserContact contact, Project project) {
		return new MemberView(contact, member.getRole(), project.getOwnerId().equals(member.getUserId()),
				member.getJoinedAt());
	}

	/** Changes requested by a partial update (already trimmed and validated). */
	public record ProjectChanges(boolean nameSet, String name, boolean descriptionSet, String description) {
	}

	/** {@code ProjectMember} in openapi.yaml. */
	public record MemberView(UserContact user, ProjectRole role, boolean isOwner, Instant joinedAt) {
	}

}
