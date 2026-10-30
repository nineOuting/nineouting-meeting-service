package com.nineouting.backend.meeting.config;

import com.nineouting.backend.meeting.client.AiServiceClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration
public class AiClientConfig {

    /** ai-service가 느려도 회의 진행이 막히지 않게 타임아웃을 둔다 */
    @Bean
    AiServiceClient aiServiceClient(RestClient.Builder builder, AiFeedProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(properties.connectTimeout());
        requestFactory.setReadTimeout(properties.readTimeout());
        RestClient.Builder client = builder.clone().requestFactory(requestFactory);
        if (properties.enabled()) {
            client.baseUrl(properties.baseUrl());
        }
        return new AiServiceClient(client.build());
    }
}
