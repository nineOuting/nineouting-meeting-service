package com.nineouting.backend.meeting.client;

/** user-service /internal/users 응답 항목 */
public record UserSummary(Long userId, String name, String email, String profileImageUrl) {
}
