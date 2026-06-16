package com.nowait.global.s3;

import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

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

    public String generatePresignedGetUrl(String imageKey) {
    GetObjectRequest getObjectRequest = GetObjectRequest.builder()
        .bucket(bucket)
        .key(imageKey)
        .build();

    GetObjectPresignRequest presignRequest = GetObjectPresignRequest.builder()
        .signatureDuration(Duration.ofMinutes(expiryMinutes))
        .getObjectRequest(getObjectRequest)
        .build();

    return s3Presigner.presignGetObject(presignRequest).url().toString();
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
            return generatePresignedGetUrl(storedImageUrl);
        }

        // 이미 S3 직접 URL로 저장된 기존 데이터 보정
        String s3Prefix = "https://" + bucket + ".s3." + regionPart() + ".amazonaws.com/";
        if (storedImageUrl.startsWith(s3Prefix)) {
            String imageKey = storedImageUrl.substring(s3Prefix.length());
            return generatePresignedGetUrl(imageKey);
        }

        return storedImageUrl;
    }

    private String regionPart() {
        return System.getenv().getOrDefault("AWS_S3_REGION", "ap-northeast-2");
    }
}
