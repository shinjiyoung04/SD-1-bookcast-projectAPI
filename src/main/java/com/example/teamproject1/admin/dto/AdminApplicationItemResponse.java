package com.example.teamproject1.admin.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record AdminApplicationItemResponse(
        Long applicationId,
        Long applicantUserId,
        String applicantLoginId,
        String applicantName,
        String title,
        String author,
        String publisher,
        String isbn,
        LocalDate publishedDate,
        Long libraryId,
        String libraryName,
        String reason,
        String status,
        String adminComment,
        LocalDateTime createdAt,
        LocalDateTime processedAt,
        long voteCount,
        Long predictionId,
        Double approvalProbability,
        Double popularityScore,
        Double voteAdjustment,
        Double finalScore,
        String modelVersion
) {
}
