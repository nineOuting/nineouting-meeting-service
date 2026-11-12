package com.nineouting.backend.meeting.service;

import com.nineouting.backend.common.client.UserClient;
import com.nineouting.backend.common.client.UserSummary;
import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.meeting.domain.Meeting;
import com.nineouting.backend.meeting.domain.MeetingMaterial;
import com.nineouting.backend.meeting.domain.MeetingStatus;
import com.nineouting.backend.meeting.dto.MaterialResponse;
import com.nineouting.backend.meeting.dto.MaterialUploadRequest;
import com.nineouting.backend.meeting.dto.MaterialUploadResponse;
import com.nineouting.backend.meeting.dto.PersonResponse;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import com.nineouting.backend.meeting.repository.MeetingMaterialRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * REQ-067 회의 사전 자료 (MSG-067). 참석자가 올리고, 회의를 볼 수 있는 사람은 누구나 내려받는다.
 * 지우기는 올린 사람과 회의를 만든 사람. 파일 하나 50MB, 회의마다 20개까지.
 * REQ-187 다 올라간 자료는 챗봇 검색용 저장소에 넣고, 목록에 검색 상태를 보여 준다.
 */
@Service
@RequiredArgsConstructor
public class MaterialService {

    public static final long MAX_BYTES = 50L * 1024 * 1024;
    static final int MAX_COUNT = 20;
    private static final String KEY_PREFIX = "meeting-materials/";

    private final MeetingMaterialRepository repository;
    private final MaterialStorage storage;
    private final MeetingAccess access;
    private final UserClient userClient;
    private final MaterialRagSync ragSync;
    private final Clock clock;

    /** 업로드 주소를 준다. 브라우저가 다 올린 뒤 complete를 불러야 목록에 보인다 */
    @Transactional
    public MaterialUploadResponse prepare(Long meetingId, Long userId, MaterialUploadRequest request) {
        Meeting meeting = access.requireAttendee(meetingId, userId);
        // 취소된 회의에는 자료를 올리지 않는다 (끝난 회의는 회의 뒤에 자료를 나눌 수 있어 받는다)
        if (meeting.is(MeetingStatus.CANCELLED)) {
            throw new BusinessException(MeetingErrorCode.MEETING_CANCELLED);
        }
        if (request.size() > MAX_BYTES) {
            throw new BusinessException(MeetingErrorCode.MATERIAL_TOO_LARGE);
        }
        if (repository.countByMeetingIdAndStatus(meetingId, MeetingMaterial.Status.READY) >= MAX_COUNT) {
            throw new BusinessException(MeetingErrorCode.MATERIAL_LIMIT_EXCEEDED);
        }
        String key = KEY_PREFIX + meetingId + "/" + UUID.randomUUID();
        MeetingMaterial material = repository.save(new MeetingMaterial(meetingId, request.fileName(),
                request.contentType(), request.size(), key, userId, now()));
        return new MaterialUploadResponse(material.getId(), storage.uploadUrl(key, request.contentType()),
                request.contentType());
    }

    /** 올라간 파일의 실제 크기를 확인한다. 50MB를 넘으면 지우고 거절한다 */
    @Transactional
    public MaterialResponse complete(Long meetingId, Long userId, Long materialId) {
        Meeting meeting = access.requireAttendee(meetingId, userId);
        MeetingMaterial material = materialOf(meetingId, materialId);
        if (!material.getUploadedBy().equals(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        if (!material.isReady()) {
            MaterialStorage.StoredFile file = storage.head(material.getStorageKey())
                    .orElseThrow(() -> new BusinessException(MeetingErrorCode.MATERIAL_NOT_UPLOADED));
            if (file.size() > MAX_BYTES) {
                storage.deleteQuietly(material.getStorageKey());
                repository.delete(material);
                throw new BusinessException(MeetingErrorCode.MATERIAL_TOO_LARGE);
            }
            // REQ-187 챗봇 검색용 저장소에 넣을 차례로 둔다 (MaterialRagSync가 보낸다)
            material.ready(file.size(), file.etag(), now());
        }
        return response(material, users(List.of(material), meeting.getWorkspaceId()));
    }

    @Transactional(readOnly = true)
    public List<MaterialResponse> list(Long meetingId, Long userId) {
        Meeting meeting = access.requireViewable(meetingId, userId);
        List<MeetingMaterial> materials = repository.findByMeetingIdAndStatusOrderById(meetingId,
                MeetingMaterial.Status.READY);
        Map<Long, UserSummary> users = users(materials, meeting.getWorkspaceId());
        return materials.stream().map(material -> response(material, users)).toList();
    }

    @Transactional
    public void delete(Long meetingId, Long userId, Long materialId) {
        Meeting meeting = access.requireViewable(meetingId, userId);
        MeetingMaterial material = materialOf(meetingId, materialId);
        if (!material.getUploadedBy().equals(userId) && !meeting.isOwnedBy(userId)) {
            throw new BusinessException(CommonErrorCode.FORBIDDEN);
        }
        storage.deleteQuietly(material.getStorageKey());
        repository.delete(material);
        ragSync.deleteQuietly(material.getId());
    }

    private MeetingMaterial materialOf(Long meetingId, Long materialId) {
        return repository.findById(materialId)
                .filter(material -> material.getMeetingId().equals(meetingId))
                .orElseThrow(() -> new BusinessException(CommonErrorCode.NOT_FOUND));
    }

    /** 올린 사람은 워크스페이스 프로필로 보여 준다 (REQ-114, 115, 118) */
    private Map<Long, UserSummary> users(List<MeetingMaterial> materials, Long workspaceId) {
        if (materials.isEmpty()) {
            return Map.of();
        }
        return userClient.findUsers(materials.stream().map(MeetingMaterial::getUploadedBy).collect(Collectors.toSet()),
                workspaceId);
    }

    private MaterialResponse response(MeetingMaterial material, Map<Long, UserSummary> users) {
        return new MaterialResponse(material.getId(), material.getFileName(), material.getContentType(),
                material.getSizeBytes(), PersonResponse.of(material.getUploadedBy(), users.get(material.getUploadedBy())),
                material.getCreatedAt(), storage.downloadUrl(material.getStorageKey(), material.getFileName()),
                ragSync.searchStatus(material));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }
}
