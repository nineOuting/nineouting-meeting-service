package com.nineouting.backend.meeting.dto;

import com.nineouting.backend.meeting.client.UserSummary;

/** 참석자·회의록 작성자 표시용. 탈퇴 등으로 user-service에 없으면 이름이 null이다. */
public record PersonResponse(Long userId, String name, String profileImageUrl) {

    public static PersonResponse of(Long userId, UserSummary user) {
        return user == null
                ? new PersonResponse(userId, null, null)
                : new PersonResponse(userId, user.name(), user.profileImageUrl());
    }
}
