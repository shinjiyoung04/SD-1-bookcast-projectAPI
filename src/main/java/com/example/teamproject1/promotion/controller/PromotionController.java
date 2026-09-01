package com.example.teamproject1.promotion.controller;

import com.example.teamproject1.promotion.dto.PromotionCreateRequest;
import com.example.teamproject1.promotion.dto.PromotionDecisionRequest;
import com.example.teamproject1.promotion.dto.PromotionResponse;
import com.example.teamproject1.promotion.entity.PromotionStatus;
import com.example.teamproject1.promotion.service.PromotionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/promotions")
public class PromotionController {

    private final PromotionService promotionService;

    @PostMapping
    public ResponseEntity<PromotionResponse> create(
            @Valid
            @RequestBody
            PromotionCreateRequest request
    ) {
        return ResponseEntity.ok(
                promotionService.create(request)
        );
    }

    @GetMapping("/me")
    public ResponseEntity<PromotionResponse> getMyLatest(
            @RequestParam Long userId
    ) {
        PromotionResponse response =
                promotionService.getMyLatest(userId);

        return ResponseEntity.ok(response);
    }

    @GetMapping
    public ResponseEntity<List<PromotionResponse>> getRequests(
            @RequestParam Long masterAdminId,
            @RequestParam(required = false)
            PromotionStatus status
    ) {
        return ResponseEntity.ok(
                promotionService.getRequests(
                        masterAdminId,
                        status
                )
        );
    }

    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<PromotionResponse> cancel(
            @PathVariable Long requestId,
            @RequestParam Long userId
    ) {
        return ResponseEntity.ok(
                promotionService.cancel(
                        requestId,
                        userId
                )
        );
    }

    @PatchMapping("/{requestId}/approve")
    public ResponseEntity<PromotionResponse> approve(
            @PathVariable Long requestId,
            @Valid
            @RequestBody
            PromotionDecisionRequest request
    ) {
        return ResponseEntity.ok(
                promotionService.approve(
                        requestId,
                        request.masterAdminId(),
                        request.comment()
                )
        );
    }

    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<PromotionResponse> reject(
            @PathVariable Long requestId,
            @Valid
            @RequestBody
            PromotionDecisionRequest request
    ) {
        return ResponseEntity.ok(
                promotionService.reject(
                        requestId,
                        request.masterAdminId(),
                        request.comment()
                )
        );
    }
}