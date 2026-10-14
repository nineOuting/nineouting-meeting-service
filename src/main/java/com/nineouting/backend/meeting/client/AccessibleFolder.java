package com.nineouting.backend.meeting.client;

/** workspace-service /internal/users/{userId}/folders 응답 항목 */
public record AccessibleFolder(Long folderId, Long workspaceId, String workspaceName, Long parentId, String name) {
}
