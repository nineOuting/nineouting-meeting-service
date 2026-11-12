package com.nineouting.backend.meeting.config;

import java.net.URI;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/** 사전 자료용 S3 클라이언트 (user-service 프로필 사진과 같은 방식) */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    S3Client s3Client(StorageProperties properties) {
        var builder = S3Client.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties));
        if (properties.hasEndpoint()) {
            builder.endpointOverride(URI.create(properties.endpoint())).forcePathStyle(true);
        }
        return builder.build();
    }

    /** 브라우저에 줄 주소와 ai-service에 줄 주소 (로컬 통합 환경에서만 다르다) */
    @Bean(destroyMethod = "close")
    MaterialPresigners materialPresigners(StorageProperties properties) {
        S3Presigner browser = presigner(properties, properties.presignEndpointOrDefault());
        S3Presigner internal = properties.hasEndpoint() && !properties.endpoint().equals(properties.presignEndpointOrDefault())
                ? presigner(properties, properties.endpoint()) : browser;
        return new MaterialPresigners(browser, internal);
    }

    private static S3Presigner presigner(StorageProperties properties, String endpoint) {
        var builder = S3Presigner.builder()
                .region(Region.of(properties.region()))
                .credentialsProvider(credentials(properties));
        if (properties.hasEndpoint()) {
            // LocalStack은 버킷을 경로에 넣는 방식(path-style)만 지원한다
            builder.endpointOverride(URI.create(endpoint))
                    .serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build());
        }
        return builder.build();
    }

    /**
     * browser: 업로드, 내려받기 주소 (브라우저가 닿는 쪽). internal: ai-service가 사전 자료를 받을 주소 (REQ-187).
     * AWS에서는 둘이 같다.
     */
    public record MaterialPresigners(S3Presigner browser, S3Presigner internal) implements AutoCloseable {

        @Override
        public void close() {
            browser.close();
            if (internal != browser) {
                internal.close();
            }
        }
    }

    private static AwsCredentialsProvider credentials(StorageProperties properties) {
        if (properties.hasStaticCredentials()) {
            return StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
        }
        return DefaultCredentialsProvider.builder().build();
    }
}
