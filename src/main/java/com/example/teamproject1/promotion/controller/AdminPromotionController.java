package com.example.teamproject1.promotion.controller;

import com.example.teamproject1.promotion.dto.*;
import com.example.teamproject1.promotion.entity.PromotionStatus;
import com.example.teamproject1.promotion.service.AdminPromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin-promotions")
@RequiredArgsConstructor
public class AdminPromotionController {
    private final AdminPromotionService service;

    @PostMapping
    public ResponseEntity<PromotionRequestResponseDTO> create(@RequestBody PromotionRequestCreateDTO dto) {
        return ResponseEntity.ok(service.create(dto));
    }

    @GetMapping("/mine")
    public ResponseEntity<PromotionRequestResponseDTO> myLatest(@RequestParam Long userId) {
        return ResponseEntity.ok(service.myLatest(userId));
    }

    @GetMapping
    public ResponseEntity<List<PromotionRequestResponseDTO>> list(
            @RequestParam Long masterUserId,
            @RequestParam(required = false) PromotionStatus status) {
        return ResponseEntity.ok(service.list(status, masterUserId));
    }

    @PatchMapping("/{requestId}/approve")
    public ResponseEntity<PromotionRequestResponseDTO> approve(@PathVariable Long requestId, @RequestBody PromotionDecisionDTO dto) {
        return ResponseEntity.ok(service.approve(requestId, dto));
    }

    @PatchMapping("/{requestId}/reject")
    public ResponseEntity<PromotionRequestResponseDTO> reject(@PathVariable Long requestId, @RequestBody PromotionDecisionDTO dto) {
        return ResponseEntity.ok(service.reject(requestId, dto));
    }

    @PatchMapping("/{requestId}/cancel")
    public ResponseEntity<PromotionRequestResponseDTO> cancel(@PathVariable Long requestId, @RequestParam Long userId) {
        return ResponseEntity.ok(service.cancel(requestId, userId));
    }
}
