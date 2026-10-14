package com.nineouting.backend.meeting.client;

import com.nineouting.backend.common.web.error.BusinessException;
import com.nineouting.backend.common.web.response.ApiResponse;
import com.nineouting.backend.meeting.config.MeetingProperties;
import com.nineouting.backend.meeting.error.MeetingErrorCode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * 사용자 이름·이메일은 user-service가 가지고 있으므로 필요할 때 조회한다 (회의는 사용자 ID만 저장).
 */
@Component
public class UserClient {

    /** user-service가 한 번에 받는 최대 개수 */
    private static final int BATCH_SIZE = 100;
    private static final ParameterizedTypeReference<ApiResponse<List<UserSummary>>> RESPONSE_TYPE =
            new ParameterizedTypeReference<>() { };

    private final RestClient restClient;

    public UserClient(RestClient.Builder builder, MeetingProperties properties) {
        this.restClient = builder.baseUrl(properties.userServiceUrl()).build();
    }

    /** 없는 사용자는 결과에서 빠진다. */
    public Map<Long, UserSummary> findUsers(Collection<Long> userIds) {
        List<Long> ids = userIds.stream().distinct().toList();
        Map<Long, UserSummary> result = new HashMap<>();
        for (int from = 0; from < ids.size(); from += BATCH_SIZE) {
            fetch(ids.subList(from, Math.min(from + BATCH_SIZE, ids.size())))
                    .forEach(user -> result.put(user.userId(), user));
        }
        return result;
    }

    private List<UserSummary> fetch(List<Long> ids) {
        try {
            ApiResponse<List<UserSummary>> response = restClient.get()
                    .uri(uri -> uri.path("/internal/users")
                            .queryParam("ids", ids.stream().map(String::valueOf).collect(Collectors.joining(",")))
                            .build())
                    .retrieve()
                    .body(RESPONSE_TYPE);
            return response == null || response.data() == null ? new ArrayList<>() : response.data();
        } catch (RestClientException e) {
            throw new BusinessException(MeetingErrorCode.USER_SERVICE_UNAVAILABLE);
        }
    }
}
