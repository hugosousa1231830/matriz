package com.matriz.projects;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

interface ProjectMemberRepository extends JpaRepository<ProjectMember, ProjectMember.Key> {

	List<ProjectMember> findByIdProjectId(UUID projectId);

}
