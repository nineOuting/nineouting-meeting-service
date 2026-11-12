package com.nineouting.backend.meeting.config;

import com.nineouting.backend.meeting.client.AiServiceClient;
import com.nineouting.backend.meeting.client.MinutesDocsClient;
import com.nineouting.backend.meeting.client.RagClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfig {

    /** ai-service가 느려도 회의 진행이 막히지 않게 타임아웃을 둔다 */
    @Bean
    AiServiceClient aiServiceClient(RestClient.Builder builder, AiFeedProperties properties) {
        return new AiServiceClient(aiClient(builder, properties, properties.readTimeout()));
    }

    /** RAG 넣기는 바로 답하므로 회의 중 기능과 같은 타임아웃, 챗봇 질문만 길게 (REQ-185) */
    @Bean
    RagClient ragClient(RestClient.Builder builder, AiFeedProperties properties, RagProperties ragProperties) {
        return new RagClient(aiClient(builder, properties, properties.readTimeout()),
                aiClient(builder, properties, ragProperties.chatTimeout()));
    }

    @Bean
    MinutesDocsClient minutesDocsClient(RestClient.Builder builder, AiFeedProperties properties,
                                        RagProperties ragProperties) {
        return new MinutesDocsClient(builder.clone()
                .requestFactory(requestFactory(properties.connectTimeout(), properties.readTimeout()))
                .baseUrl(ragProperties.minutesServiceUrl())
                .build());
    }

    private static RestClient aiClient(RestClient.Builder builder, AiFeedProperties properties, Duration readTimeout) {
        RestClient.Builder client = builder.clone().requestFactory(requestFactory(properties.connectTimeout(), readTimeout));
        if (properties.enabled()) {
            client.baseUrl(properties.baseUrl());
        }
        return client.build();
    }

    private static SimpleClientHttpRequestFactory requestFactory(Duration connectTimeout, Duration readTimeout) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        return requestFactory;
    }
}
