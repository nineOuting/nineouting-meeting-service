package com.nineouting.backend.meeting.service;

import com.nineouting.backend.meeting.config.StorageProperties;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

/**
 * 사전 자료는 서버를 거치지 않고 브라우저가 S3에 직접 올리고 받는다(presigned URL).
 * 버킷은 비공개이고, 올리고 받는 주소는 잠깐만 쓸 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MaterialStorage {

    private final S3Client s3Client;
    private final S3Presigner s3Presigner;
    private final StorageProperties properties;

    public String uploadUrl(String key, String contentType) {
        return s3Presigner.presignPutObject(request -> request
                        .signatureDuration(properties.uploadUrlTtl())
                        .putObjectRequest(put -> put.bucket(properties.bucket()).key(key).contentType(contentType)))
                .url().toString();
    }

    /** 받을 때 원래 파일 이름으로 저장되게 한다 (한글 이름은 RFC 5987 방식) */
    public String downloadUrl(String key, String fileName) {
        String encoded = URLEncoder.encode(fileName, StandardCharsets.UTF_8).replace("+", "%20");
        return s3Presigner.presignGetObject(request -> request
                        .signatureDuration(properties.downloadUrlTtl())
                        .getObjectRequest(get -> get.bucket(properties.bucket()).key(key)
                                .responseContentDisposition("attachment; filename*=UTF-8''" + encoded)))
                .url().toString();
    }

    /** 올라간 파일의 크기(바이트). 아직 올라가지 않았으면 빈 값 */
    public Optional<Long> size(String key) {
        try {
            return Optional.of(s3Client.headObject(head -> head.bucket(properties.bucket()).key(key)).contentLength());
        } catch (NoSuchKeyException e) {
            return Optional.empty();
        }
    }

    /** 지우기 실패는 사용자 요청을 막을 이유가 없으므로 기록만 남긴다 */
    public void deleteQuietly(String key) {
        try {
            s3Client.deleteObject(delete -> delete.bucket(properties.bucket()).key(key));
        } catch (SdkException e) {
            log.warn("사전 자료 삭제 실패: {}", key, e);
        }
    }
}
