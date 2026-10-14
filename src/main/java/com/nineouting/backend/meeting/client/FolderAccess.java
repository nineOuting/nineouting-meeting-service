package com.nineouting.backend.meeting.client;

/** workspace-service /internal/folders/{folderId}/access 응답 */
public record FolderAccess(Long folderId, Long workspaceId, boolean allowed) {
}
