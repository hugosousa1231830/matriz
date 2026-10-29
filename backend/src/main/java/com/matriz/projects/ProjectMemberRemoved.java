package com.matriz.projects;

import java.util.UUID;

/**
 * Published synchronously, inside the transaction that removes a member, so
 * that the tasks module deletes the user's assignments on the project's tasks
 * in the same transaction (BR7).
 */
public record ProjectMemberRemoved(UUID projectId, UUID userId) {
}
