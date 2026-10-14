package com.nineouting.backend.meeting.client;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.error.CommonErrorCode;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.config.MeetingProperties;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import java.util.List;
import java.util.Set;
import java.util.function.Supplier;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 폴더 권한은 workspace-service가 가지고 있으므로 필요할 때 조회한다 (회의는 폴더 ID만 저장).
 * 없는 폴더는 404, workspace-service에 연결할 수 없으면 503으로 바꾼다.
 */
@Component
public class WorkspaceClient {

    private static final ParameterizedTypeReference<ApiResponse<FolderAccess>> ACCESS_TYPE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<Set<Long>>> MEMBERS_TYPE =
            new ParameterizedTypeReference<>() { };
    private static final ParameterizedTypeReference<ApiResponse<List<AccessibleFolder>>> FOLDERS_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public WorkspaceClient(RestClient.Builder builder, MeetingProperties properties) {
        this.restClient = builder.baseUrl(properties.workspaceServiceUrl()).build();
    }

    /** 사용자가 폴더에 들어갈 수 있는지와 폴더의 워크스페이스 */
    public FolderAccess access(Long folderId, Long userId) {
        return call(() -> restClient.get()
                .uri(uri -> uri.path("/internal/folders/{folderId}/access").queryParam("userId", userId).build(folderId))
                .retrieve()
                .body(ACCESS_TYPE));
    }

    /** 폴더에 들어갈 수 있는 사람 전체 = 참석자로 고를 수 있는 사람 (REQ-058) */
    public Set<Long> folderMembers(Long folderId) {
        return call(() -> restClient.get()
                .uri("/internal/folders/{folderId}/members", folderId)
                .retrieve()
                .body(MEMBERS_TYPE));
    }

    /** 사용자가 들어갈 수 있는 모든 워크스페이스의 폴더 (홈 캘린더, REQ-018) */
    public List<AccessibleFolder> accessibleFolders(Long userId) {
        return call(() -> restClient.get()
                .uri("/internal/users/{userId}/folders", userId)
                .retrieve()
                .body(FOLDERS_TYPE));
    }

    private static <T> T call(Supplier<ApiResponse<T>> request) {
        try {
            ApiResponse<T> response = request.get();
            if (response == null || response.data() == null) {
                throw new BusinessException(MeetingErrorCode.WORKSPACE_SERVICE_UNAVAILABLE);
            }
            return response.data();
        } catch (HttpClientErrorException.NotFound e) {
            throw new BusinessException(CommonErrorCode.NOT_FOUND);
        } catch (RestClientException e) {
            throw new BusinessException(MeetingErrorCode.WORKSPACE_SERVICE_UNAVAILABLE);
        }
    }
}
