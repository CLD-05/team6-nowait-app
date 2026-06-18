package com.nowait.global.s3;

import com.nowait.domain.restaurant.service.RestaurantService;
import com.nowait.global.security.principal.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/images")
public class ImageController {

    private final S3Service s3Service;
    private final RestaurantService restaurantService;

    @Value("${aws.s3.bucket}")
    private String bucket;

    @Value("${aws.s3.region}")
    private String region;

    /**
     * Presigned URL 발급
     * POST /api/v1/images/presigned-url?restaurantId={id}&filename={filename}
     */
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/presigned-url")
    public ResponseEntity<PresignedUrlResponse> getPresignedUrl(
        @RequestParam Long restaurantId,
        @RequestParam String filename,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        restaurantService.verifyOwner(restaurantId, userDetails.getUserId());
        S3Service.PresignedUrlResult result = s3Service.generatePresignedUrl(restaurantId, filename);
        return ResponseEntity.ok(new PresignedUrlResponse(result.presignedUrl(), result.imageKey()));
    }

    /**
     * S3 업로드 완료 후 imageKey DB 저장
     * POST /api/v1/images/complete
     */
    @PreAuthorize("hasRole('OWNER')")
    @PostMapping("/complete")
    public ResponseEntity<ImageCompleteResponse> completeUpload(
        @Valid @RequestBody ImageCompleteRequest request,
        @AuthenticationPrincipal CustomUserDetails userDetails
    ) {
        String imageKey = request.imageKey();

        restaurantService.updateRestaurantImage(
            request.restaurantId(),
            userDetails.getUserId(),
            imageKey
        );

        String readableImageUrl = s3Service.buildPublicUrl(imageKey);

        return ResponseEntity.ok(new ImageCompleteResponse(readableImageUrl));
    }

    public record ImageCompleteResponse(String imageUrl) {}
}
