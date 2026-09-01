package com.example.teamproject1.promotion.dto;

import com.example.teamproject1.promotion.entity.PromotionStatus;

import java.time.LocalDateTime;

public record PromotionResponse(
        Long requestId,

        Long userId,
        String loginId,
        String name,
        String email,

        String libraryName,
        String libraryCode,
        String department,
        String employeeNumber,
        String contact,
        String reason,

        PromotionStatus status,

        Long masterAdminId,
        String masterAdminName,
        String masterComment,

        LocalDateTime createdAt,
        LocalDateTime processedAt,
        LocalDateTime updatedAt
) {
}