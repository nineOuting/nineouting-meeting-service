package com.nineouting.backend.meeting;

import com.nineouting.backend.common.event.meeting.MeetingCancelled;
import com.nineouting.backend.common.event.meeting.MeetingCreated;
import com.nineouting.backend.common.event.meeting.MeetingFinished;
import com.nineouting.backend.common.event.meeting.MeetingMoved;
import com.nineouting.backend.common.event.meeting.MeetingUpdated;
import com.nineouting.backend.common.event.minutes.MinutesConfirmed;
import com.nineouting.backend.common.event.minutes.MinutesReviewRequested;
import com.nineouting.backend.common.event.workspace.FolderDeleted;
import com.nineouting.backend.common.security.jwt.JwksKeyLocator;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Jwks;
import java.security.KeyPair;
import java.security.interfaces.RSAPublicKey;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistrar;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.localstack.LocalStackContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

/**
 * 테스트용 PostgreSQL·LocalStack(SNS·SQS)·Redis, user-service 대신 토큰을 서명할 키.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    private static final KeyPair KEYS = Jwts.SIG.RS256.keyPair().build();
    private static final String KID = "test-key";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer("postgres:17.6-alpine");
    }

    /** 회의방 메시지를 인스턴스끼리 나눠 보내는 Pub/Sub */
    @Bean
    @ServiceConnection(name = "redis")
    GenericContainer<?> redisContainer() {
        return new GenericContainer<>(DockerImageName.parse("redis:7.4-alpine")).withExposedPorts(6379);
    }

    @Bean
    LocalStackContainer localStackContainer() {
        return new LocalStackContainer(DockerImageName.parse("localstack/localstack:4.4")).withServices("sns", "sqs");
    }

    @Bean
    SnsClient snsClient(LocalStackContainer localStack) {
        return SnsClient.builder().endpointOverride(localStack.getEndpoint()).region(Region.of(localStack.getRegion()))
                .credentialsProvider(credentials(localStack)).build();
    }

    @Bean
    SqsClient sqsClient(LocalStackContainer localStack) {
        return SqsClient.builder().endpointOverride(localStack.getEndpoint()).region(Region.of(localStack.getRegion()))
                .credentialsProvider(credentials(localStack)).build();
    }

    @Bean
    LocalMessaging localMessaging(SnsClient snsClient, SqsClient sqsClient) {
        return new LocalMessaging(snsClient, sqsClient);
    }

    /** 보내는 토픽을 만들어 outbox에 토픽 ARN 앞부분을, 이 서비스 큐를 만들어 받는 토픽에 구독시키고 inbox에 큐 주소를 알려 준다 (운영은 Terraform) */
    @Bean
    DynamicPropertyRegistrar messagingProperties(LocalMessaging messaging) {
        return registry -> {
            messaging.topicArn(MeetingCreated.TOPIC);
            messaging.topicArn(MeetingUpdated.TOPIC);
            messaging.topicArn(MeetingCancelled.TOPIC);
            messaging.topicArn(MeetingFinished.TOPIC);
            messaging.topicArn(MeetingMoved.TOPIC);
            String topicPrefix = messaging.topicArnPrefix(MeetingCreated.TOPIC);
            registry.add("nineouting.outbox.topic-prefix", () -> topicPrefix);
            String queueUrl = messaging.queue("meeting-service", List.of(
                    FolderDeleted.TOPIC,
                    MinutesReviewRequested.TOPIC,
                    MinutesConfirmed.TOPIC));
            registry.add("nineouting.inbox.queue", () -> queueUrl);
        };
    }

    private static StaticCredentialsProvider credentials(LocalStackContainer localStack) {
        return StaticCredentialsProvider.create(AwsBasicCredentials.create(localStack.getAccessKey(), localStack.getSecretKey()));
    }

    /** security-common이 user-service JWKS 대신 테스트 공개키로 검증하게 한다. */
    @Bean
    JwksKeyLocator jwksKeyLocator() {
        String jwks = "{\"keys\":[" + Jwks.json(Jwks.builder().key((RSAPublicKey) KEYS.getPublic()).id(KID).build()) + "]}";
        return new JwksKeyLocator(() -> jwks);
    }

    public static String token(Long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .header().keyId(KID).and()
                .issuer("nineouting-user-service")
                .subject(userId.toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(1800)))
                .signWith(KEYS.getPrivate(), Jwts.SIG.RS256)
                .compact();
    }
}
