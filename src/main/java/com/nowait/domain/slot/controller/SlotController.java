// domain/slot/controller/SlotController.java
package com.nowait.domain.slot.controller;

import com.nowait.domain.slot.dto.SlotCreateRequest;
import com.nowait.domain.slot.dto.SlotResponse;
import com.nowait.domain.slot.dto.SlotUpdateRequest;
import com.nowait.domain.slot.service.SlotService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SlotController {

    private final SlotService slotService;

    // 예약 가능 슬롯 조회
    @GetMapping("/restaurants/{restaurantId}/slots")
    public ResponseEntity<SlotResponse> getSlots(
            @PathVariable Long restaurantId,
            @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
            LocalDate date) {
        return ResponseEntity.ok(slotService.getSlots(restaurantId, date));
    }

    // 슬롯 생성 (점주 전용)
    @PostMapping("/owner/restaurants/{restaurantId}/slots")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<SlotResponse.SlotInfo> createSlot(
            @PathVariable Long restaurantId,
            @RequestBody @Valid SlotCreateRequest request) {
        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(slotService.createSlot(restaurantId, request));
    }

    // 슬롯 수정 (점주 전용)
    @PatchMapping("/owner/slots/{slotId}")
    @PreAuthorize("hasRole('OWNER')")
    public ResponseEntity<SlotResponse.SlotInfo> updateSlot(
            @PathVariable Long slotId,
            @RequestBody @Valid SlotUpdateRequest request) {
        return ResponseEntity.ok(slotService.updateSlot(slotId, request));
    }
}