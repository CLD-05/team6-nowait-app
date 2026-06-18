package com.nowait.global.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

import java.time.Duration;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class S3Service {

    private final S3Presigner s3Presigner;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.presigned-url-expiry-minutes}")
    private int expiryMinutes;

    /**
     * 이미지 업로드용 Presigned PUT URL 발급.
     * imageKey 형식: restaurants/{restaurantId}/{uuid}.{ext}
     */
    public PresignedUrlResult generatePresignedUrl(Long restaurantId, String originalFilename) {
        String ext = extractExtension(originalFilename);
        String imageKey = "restaurants/" + restaurantId + "/" + UUID.randomUUID() + "." + ext;

        PutObjectRequest putRequest = PutObjectRequest.builder()
            .bucket(bucket)
            .key(imageKey)
            .contentType(resolveContentType(ext))
            .build();

        PutObjectPresignRequest presignRequest = PutObjectPresignRequest.builder()
            .signatureDuration(Duration.ofMinutes(expiryMinutes))
            .putObjectRequest(putRequest)
            .build();

        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(presignRequest);
        return new PresignedUrlResult(presigned.url().toString(), imageKey);
    }

    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) return "jpg";
        return filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
    }

    private String resolveContentType(String ext) {
        return switch (ext) {
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            default -> "image/jpeg";
        };
    }

    public record PresignedUrlResult(String presignedUrl, String imageKey) {}

    /*
     * 식당 이미지는 비공개 정보가 아니므로 presigned GET URL(만료 있음) 대신
     * 영구 공개 URL을 돌려준다. presigned URL을 API 응답/프론트 상태에 들고 있다가
     * 만료 후 403이 나는 문제를 근본적으로 없앤다. 버킷은 restaurants/* 경로에 대해
     * 공개 읽기를 허용하도록 설정되어 있어야 한다.
     */
    public String buildPublicUrl(String imageKey) {
        return "https://" + bucket + ".s3." + regionPart() + ".amazonaws.com/" + imageKey;
    }

    public String resolveReadableImageUrl(String storedImageUrl) {
        if (storedImageUrl == null || storedImageUrl.isBlank()) {
            return storedImageUrl;
        }

        // 기존 seed 이미지: Spring Boot static resource
        if (storedImageUrl.startsWith("/images/")) {
            return storedImageUrl;
        }

        // 새 업로드 이미지: S3 key
        if (storedImageUrl.startsWith("restaurants/")) {
            return buildPublicUrl(storedImageUrl);
        }

        // 이미 S3 직접 URL로 저장된 기존 데이터(과거 presigned URL 포함) 보정
        String s3Prefix = "https://" + bucket + ".s3." + regionPart() + ".amazonaws.com/";
        if (storedImageUrl.startsWith(s3Prefix)) {
            String imageKey = storedImageUrl.substring(s3Prefix.length());
            // 과거에 presigned 쿼리스트링까지 저장된 행이 있을 수 있어 키 부분만 추출
            int queryIndex = imageKey.indexOf('?');
            if (queryIndex >= 0) {
                imageKey = imageKey.substring(0, queryIndex);
            }
            return buildPublicUrl(imageKey);
        }

        return storedImageUrl;
    }

    private String regionPart() {
        return System.getenv().getOrDefault("AWS_S3_REGION", "ap-northeast-2");
    }
}
