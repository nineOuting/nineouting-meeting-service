package com.nineouting.backend.meeting.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param userServiceUrl      사용자 이름 조회 (/internal/users)
 * @param workspaceServiceUrl 폴더 권한·폴더 멤버·폴더 목록 조회 (/internal/folders, /internal/users/{id}/folders)
 */
@ConfigurationProperties(prefix = "nineouting.meeting")
public record MeetingProperties(String userServiceUrl, String workspaceServiceUrl) {
}
